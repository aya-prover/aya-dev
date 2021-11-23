// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import kala.collection.mutable.MutableSet;
import kala.value.Ref;
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
import org.aya.concrete.resolve.ResolveInfo;
import org.aya.concrete.resolve.module.CachedModuleLoader;
import org.aya.concrete.resolve.module.FileModuleLoader;
import org.aya.concrete.stmt.QualifiedID;
import org.aya.core.def.Def;
import org.aya.core.serde.SerTerm;
import org.aya.core.serde.Serializer;
import org.aya.pretty.doc.Doc;
import org.aya.util.MutableGraph;
import org.aya.util.tyck.NonStoppingTicker;
import org.aya.util.tyck.SCCTycker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * @author kiva
 */
public class LibraryCompiler implements ImportResolver.ImportLoader {
  final @NotNull LibraryConfig library;
  final @NotNull SourceFileLocator locator;

  private final @NotNull CompilerFlags flags;
  private final @NotNull CountingReporter reporter;
  private final @NotNull DynamicSeq<Path> thisModulePath = DynamicSeq.create();
  private final @NotNull DynamicSeq<LibraryCompiler> deps = DynamicSeq.create();
  private final @NotNull ImmutableSeq<LibrarySource> sources;
  private final @NotNull United states;

  public record United(@NotNull SerTerm.DeState de, @NotNull Serializer.State ser) {}

  private LibraryCompiler(@NotNull Reporter reporter, @NotNull CompilerFlags flags, @NotNull LibraryConfig library, @NotNull United states) {
    this.reporter = reporter instanceof CountingReporter counting ? counting : new CountingReporter(reporter);
    this.flags = flags;
    this.library = library;
    this.states = states;
    var srcRoot = library.librarySrcRoot();
    this.locator = new SourceFileLocator.Module(SeqView.of(srcRoot));
    this.sources = collectSource(srcRoot).map(p -> new LibrarySource(this, p));
  }

  public static int compile(@NotNull Reporter reporter, @NotNull CompilerFlags flags, @NotNull Path libraryRoot) throws IOException {
    var config = LibraryConfigData.fromLibraryRoot(LibrarySource.canonicalize(libraryRoot));
    var compiler = new LibraryCompiler(reporter, flags, config, new United(new SerTerm.DeState(), new Serializer.State()));
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
    var owner = source.owner();
    var program = AyaParsing.program(owner.locator, owner.reporter, source.file(), true);
    var finder = new ImportResolver(owner, source);
    finder.resolveStmt(program);
  }

  private @NotNull MutableGraph<LibrarySource> resolveLibraryImports() throws IOException {
    var graph = MutableGraph.<LibrarySource>create();
    reportNest("[Info] Resolving source file dependency");
    for (var file : sources) {
      resolveImports(file);
      collectDep(graph, file);
    }
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
      var depCompiler = new LibraryCompiler(reporter, flags, depConfig, states);
      deps.append(depCompiler);
    }

    for (var dep : deps) {
      var upToDate = dep.make();
      anyDepChanged = anyDepChanged || !upToDate;
      thisModulePath.append(dep.library.libraryOutRoot());
    }

    reporter.reportString("Compiling " + library.name());
    if (anyDepChanged) deleteRecursively(library.libraryOutRoot());

    var srcRoot = library.librarySrcRoot();
    thisModulePath.append(srcRoot);

    var depGraph = resolveLibraryImports();
    return make(depGraph);
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

    var thisOutRoot = Files.createDirectories(library.libraryOutRoot());
    var loader = new LibraryModuleLoader(reporter, locator, thisModulePath.view(), thisOutRoot, new Ref<>(), states.de);
    var moduleLoader = new CachedModuleLoader(loader);
    loader.cachedSelf().value = moduleLoader;

    var delayedReporter = new DelayedReporter(reporter);
    var tycker = new LibraryNonStoppingTycker(new LibrarySccTycker(delayedReporter, this, moduleLoader), changed);
    // use delayed reporter to avoid showing errors in the middle of compilation.
    // we only show errors after all SCCs are tycked
    try (delayedReporter) {
      SCCs.forEachChecked(tycker::tyckSCC);
    }
    if (tycker.skippedSet.isNotEmpty()) {
      reporter.reportString("Failing module(s):");
      tycker.skippedSet.forEach(f -> reportNest(String.format("%s (%s)", QualifiedID.join(f.moduleName()), f.displayPath())));
      reporter.reportString("");
    }
    return false;
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
    @NotNull LibraryCompiler delegate,
    @NotNull CachedModuleLoader moduleLoader
  ) implements SCCTycker<LibrarySource, IOException> {
    @Override public @NotNull ImmutableSeq<LibrarySource> tyckSCC(@NotNull ImmutableSeq<LibrarySource> order) throws IOException {
      var reporter = new CountingReporter(outerReporter);
      for (var f : order) Files.deleteIfExists(f.coreFile());
      for (var f : order) {
        tyckOne(reporter, f);
        if (!reporter.noError()) return ImmutableSeq.of(f);
      }
      return ImmutableSeq.empty();
    }

    private void tyckOne(CountingReporter reporter, LibrarySource file) {
      var mod = resolveModule(file);
      delegate.reportNest(String.format("[Tyck] %s (%s)", QualifiedID.join(mod.thisModule().moduleName()), file.displayPath()));
      FileModuleLoader.tyckResolvedModule(mod, reporter, null,
        (moduleResolve, stmts, defs) -> {
          if (reporter.noError()) delegate.saveCompiledCore(file, moduleResolve, defs);
        });
    }

    private @NotNull ResolveInfo resolveModule(@NotNull LibrarySource file) {
      var mod = file.moduleName();
      var resolveInfo = moduleLoader.load(mod);
      if (resolveInfo == null) throw new IllegalStateException("Unable to load module: " + mod);
      return resolveInfo;
    }
  }

  private @Nullable LibrarySource findModuleFile(@NotNull ImmutableSeq<String> mod) {
    return sources.find(s -> {
      var checkMod = s.moduleName();
      return checkMod.equals(mod);
    }).getOrNull();
  }

  private void saveCompiledCore(@NotNull LibrarySource file, @NotNull ResolveInfo resolveInfo, @NotNull ImmutableSeq<Def> defs) {
    try {
      var coreFile = file.coreFile();
      AyaCompiler.saveCompiledCore(coreFile, resolveInfo, defs, states.ser);
      Timestamp.update(file);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void reportNest(@NotNull String text) {
    reporter.reportDoc(Doc.nest(2, Doc.english(text)));
  }

  private static @NotNull ImmutableSeq<Path> collectSource(@NotNull Path srcRoot) {
    try (var walk = Files.walk(srcRoot)) {
      return walk.filter(Files::isRegularFile)
        .filter(path -> path.getFileName().toString().endsWith(".aya"))
        .collect(ImmutableSeq.factory());
    } catch (IOException e) {
      return ImmutableSeq.empty();
    }
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

  private static void deleteRecursively(@NotNull Path path) throws IOException {
    if (!Files.exists(path)) return;
    try (var walk = Files.walk(path)) {
      walk.sorted(Comparator.reverseOrder())
        .collect(ImmutableSeq.factory())
        .forEachChecked(Files::deleteIfExists);
    }
  }

  @Override public @NotNull LibrarySource load(@NotNull ImmutableSeq<String> mod) {
    var file = findModuleFile(mod);
    if (file == null) for (var dep : deps) {
      file = dep.findModuleFile(mod);
      if (file != null) break;
    }
    if (file == null) throw new IllegalArgumentException("No library owns module: " + mod);
    try {
      resolveImports(file);
    } catch (IOException e) {
      throw new RuntimeException("Cannot load imported module " + mod, e);
    }
    return file;
  }
}
