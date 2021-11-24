// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import kala.collection.mutable.MutableSet;
import org.aya.api.error.CountingReporter;
import org.aya.api.error.DelayedReporter;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourceFileLocator;
import org.aya.cli.library.json.LibraryConfig;
import org.aya.cli.library.json.LibraryConfigData;
import org.aya.cli.library.json.LibraryDependency;
import org.aya.cli.single.CompilerFlags;
import org.aya.cli.utils.AyaCompiler;
import org.aya.concrete.parse.AyaParsing;
import org.aya.concrete.resolve.module.CachedModuleLoader;
import org.aya.concrete.resolve.module.ModuleLoader;
import org.aya.concrete.stmt.QualifiedID;
import org.aya.util.FileUtil;
import org.aya.util.MutableGraph;
import org.aya.util.StringUtil;
import org.aya.util.tyck.NonStoppingTicker;
import org.aya.util.tyck.SCCTycker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author kiva
 */
public class LibraryCompiler implements ImportResolver.ImportLoader {
  public static final int DEFAULT_INDENT = 2;
  final @NotNull LibraryConfig library;
  final @NotNull SourceFileLocator locator;
  final @NotNull CountingReporter reporter;
  final @NotNull CachedModuleLoader<LibraryModuleLoader> moduleLoader;

  private final @NotNull CompilerFlags flags;
  private final @NotNull DynamicSeq<Path> thisModulePath = DynamicSeq.create();
  private final @NotNull DynamicSeq<LibraryCompiler> deps = DynamicSeq.create();
  private final @NotNull ImmutableSeq<LibrarySource> sources;

  /** @return Source dirs of this module, out dirs of all dependencies. */
  public @NotNull SeqView<Path> modulePath() {
    return thisModulePath.view();
  }

  private LibraryCompiler(@NotNull Reporter reporter, @NotNull CompilerFlags flags, @NotNull LibraryConfig library, @NotNull LibraryModuleLoader.United states) {
    this.reporter = reporter instanceof CountingReporter counting ? counting : new CountingReporter(reporter);
    this.flags = flags;
    this.library = library;
    this.moduleLoader = new CachedModuleLoader<>(new LibraryModuleLoader(this, states));
    var srcRoot = library.librarySrcRoot();
    this.locator = new SourceFileLocator.Module(SeqView.of(srcRoot));
    this.sources = FileUtil.collectSource(srcRoot, ".aya").map(p -> new LibrarySource(this, p));
  }

  public static int compile(@NotNull Reporter reporter, @NotNull CompilerFlags flags, @NotNull Path libraryRoot) throws IOException {
    if (!Files.exists(libraryRoot)) {
      reporter.reportString("Specified library root does not exist: " + libraryRoot);
      return 1;
    }
    var config = LibraryConfigData.fromLibraryRoot(LibrarySource.canonicalize(libraryRoot));
    var compiler = new LibraryCompiler(reporter, flags, config, new LibraryModuleLoader.United());
    return compiler.start();
  }

  private @Nullable LibraryConfig depConfig(@NotNull LibraryDependency dep) throws IOException {
    // TODO: test only: dependency resolving should be done in package manager
    if (dep instanceof LibraryDependency.DepFile file)
      return LibraryConfigData.fromDependencyRoot(file.depRoot(), version -> depBuildRoot(dep.depName(), version));
    return null;
  }

  private @NotNull Path depBuildRoot(@NotNull String depName, @NotNull String version) {
    return library.libraryBuildRoot().resolve("deps").resolve(depName + "_" + version);
  }

  private void resolveImports(@NotNull LibrarySource source) throws IOException {
    if (source.program().value != null) return; // already parsed
    var owner = source.owner();
    var program = AyaParsing.program(owner.locator, owner.reporter, source.file());
    source.program().value = program;
    var finder = new ImportResolver(owner, source);
    finder.resolveStmt(program);
  }

  private @NotNull MutableGraph<LibrarySource> resolveLibraryImports() throws IOException {
    var graph = MutableGraph.<LibrarySource>create();
    reportNest("[Info] Resolving source file dependency");
    var startTime = System.currentTimeMillis();
    for (var file : sources) {
      resolveImports(file);
      collectDep(graph, file);
    }
    reporter.reportNest("Done in " + StringUtil.timeToString(
      System.currentTimeMillis() - startTime), DEFAULT_INDENT + 2);
    return graph;
  }

  public int start() throws IOException {
    if (flags.outputFile() != null) reporter.reportString(
      "Warning: command-line specified output file is ignored when compiling libraries.");
    if (flags.modulePaths().isNotEmpty()) reporter.reportString(
      "Warning: command-line specified module path is ignored when compiling libraries.");
    if (flags.distillInfo() != null) reporter.reportString(
      "Warning: command-line specified distill info is ignored when compiling libraries.");
    return AyaCompiler.catching(reporter, flags, this::make);
  }

  /**
   * Incrementally compiles a library without handling compilation errors.
   *
   * @return whether the library is up-to-date.
   * @apiNote The return value does not indicate whether the library is compiled successfully.
   */
  private boolean make() throws IOException {
    var anyDepChanged = false;
    // note[kiva]: the code below creates separate compiler for each dependency, which
    // should be done in the constructor. Since `depConfig` may throw IOException,
    // I decide to put them here because throwing exceptions in constructor is not a good idea.
    for (var dep : library.deps()) {
      var depConfig = depConfig(dep);
      // TODO[kiva]: should not be null if we have a proper package manager
      if (depConfig == null) {
        reporter.reportString("Skipping " + dep.depName());
        continue;
      }
      var depCompiler = new LibraryCompiler(reporter, flags, depConfig, moduleLoader.loader.states());
      deps.append(depCompiler);
    }

    for (var dep : deps) {
      var upToDate = dep.make();
      anyDepChanged = anyDepChanged || !upToDate;
      thisModulePath.append(dep.library.libraryOutRoot());
    }

    reporter.reportString("Compiling " + library.name());
    var startTime = System.currentTimeMillis();
    if (anyDepChanged) FileUtil.deleteRecursively(library.libraryOutRoot());

    var srcRoot = library.librarySrcRoot();
    thisModulePath.append(srcRoot);

    var depGraph = resolveLibraryImports();
    var make = make(depGraph);
    reporter.reportNest("Library loaded in " + StringUtil.timeToString(
      System.currentTimeMillis() - startTime), DEFAULT_INDENT + 2);
    return make;
  }

  /**
   * @return whether the library is up-to-date.
   */
  private boolean make(@NotNull MutableGraph<LibrarySource> depGraph) throws IOException {
    var changed = MutableGraph.<LibrarySource>create();
    var usage = depGraph.transpose();
    depGraph.E().keysView().forEach(s -> {
      if (Timestamp.sourceModified(s))
        collectChanged(usage, s, changed);
    });

    var SCCs = changed.topologicalOrder().view()
      .reversed().toImmutableSeq();
    // ^ top order generated from usage graph should be reversed
    // Only here we generate top order from usage graph just for efficiency
    // (transposing a graph is slower than reversing a list).
    // in other cases we always generate top order from dependency graphs
    // because usage graphs are never needed.
    if (SCCs.isEmpty()) {
      reportNest("[Info] No changes detected, no need to remake");
      return true;
    }

    Files.createDirectories(outDir());
    var delayedReporter = new DelayedReporter(reporter);
    var tycker = new LibraryNonStoppingTycker(new LibrarySccTycker(delayedReporter, moduleLoader), changed);
    // use delayed reporter to avoid showing errors in the middle of compilation.
    // we only show errors after all SCCs are tycked
    try (delayedReporter) {
      SCCs.forEachChecked(tycker::tyckSCC);
    }
    if (tycker.skippedSet.isNotEmpty()) {
      reporter.reportString("I dislike the following module(s):");
      tycker.skippedSet.forEach(f -> reportNest(String.format("%s (%s)", QualifiedID.join(f.moduleName()), f.displayPath())));
      reporter.reportString("");
    } else {
      reporter.reportString("I like these modules :)");
    }
    return false;
  }

  /** @return Out dir of this module. */
  public @NotNull Path outDir() {
    return library.libraryOutRoot();
  }

  record LibraryNonStoppingTycker(
    @NotNull LibrarySccTycker sccTycker,
    @NotNull MutableGraph<LibrarySource> usageGraph,
    @NotNull MutableSet<LibrarySource> skippedSet
  ) implements NonStoppingTicker<LibrarySource, IOException> {
    public LibraryNonStoppingTycker(@NotNull LibrarySccTycker sccTycker, @NotNull MutableGraph<LibrarySource> usage) {
      this(sccTycker, usage, MutableSet.create());
    }

    @Override public @NotNull Iterable<LibrarySource> collectUsageOf(@NotNull LibrarySource failed) {
      return usageGraph.suc(failed);
    }
  }

  record LibrarySccTycker(
    @NotNull Reporter outerReporter,
    @NotNull ModuleLoader moduleLoader
  ) implements SCCTycker<LibrarySource, IOException> {
    @Override
    public @NotNull ImmutableSeq<LibrarySource> tyckSCC(@NotNull ImmutableSeq<LibrarySource> order) throws IOException {
      var reporter = new CountingReporter(outerReporter);
      for (var f : order) Files.deleteIfExists(f.coreFile());
      for (var f : order) {
        tyckOne(reporter, f);
        if (!reporter.noError()) return ImmutableSeq.of(f);
      }
      return ImmutableSeq.empty();
    }

    private void tyckOne(CountingReporter reporter, LibrarySource file) {
      var moduleName = file.moduleName();
      var mod = moduleLoader.load(moduleName);
      if (mod == null) throw new IllegalStateException("Unable to load module: " + moduleName);
      file.resolveInfo().value = mod;
      outerReporter.reportNest("[Tyck] %s (%s)".formatted(
        QualifiedID.join(mod.thisModule().moduleName()), file.displayPath()), DEFAULT_INDENT);
    }
  }

  private @Nullable LibrarySource findModuleFileHere(@NotNull ImmutableSeq<String> mod) {
    return sources.find(s -> {
      var checkMod = s.moduleName();
      return checkMod.equals(mod);
    }).getOrNull();
  }

  private void reportNest(@NotNull String text) {
    reporter.reportNest(text, DEFAULT_INDENT);
  }

  private static void collectDep(@NotNull MutableGraph<LibrarySource> dep, @NotNull LibrarySource info) {
    dep.suc(info).appendAll(info.imports());
    info.imports().forEach(i -> collectDep(dep, i));
  }

  private static void collectChanged(
    @NotNull MutableGraph<LibrarySource> usage,
    @NotNull LibrarySource changed,
    @NotNull MutableGraph<LibrarySource> changedGraph
  ) {
    if (changedGraph.E().containsKey(changed)) return;
    changedGraph.suc(changed).appendAll(usage.suc(changed));
    usage.suc(changed).forEach(dep -> collectChanged(usage, dep, changedGraph));
  }

  @Override public @NotNull LibrarySource load(@NotNull ImmutableSeq<String> mod) {
    var file = findModuleFile(mod);
    try {
      resolveImports(file);
    } catch (IOException e) {
      throw new RuntimeException("Cannot load imported module " + mod, e);
    }
    return file;
  }

  @NotNull LibrarySource findModuleFile(@NotNull ImmutableSeq<String> mod) {
    var file = findModuleFileHere(mod);
    if (file == null) for (var dep : deps) {
      file = dep.findModuleFileHere(mod);
      if (file != null) break;
    }
    if (file == null) throw new IllegalArgumentException("No library owns module: " + mod);
    return file;
  }
}
