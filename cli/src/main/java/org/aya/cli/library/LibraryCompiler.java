// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableSet;
import org.aya.api.error.CountingReporter;
import org.aya.api.error.DelayedReporter;
import org.aya.api.error.Reporter;
import org.aya.cli.library.json.LibraryConfigData;
import org.aya.cli.library.source.DiskLibraryOwner;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.cli.single.CompilerFlags;
import org.aya.cli.utils.AyaCompiler;
import org.aya.concrete.parse.AyaParsing;
import org.aya.concrete.resolve.module.CachedModuleLoader;
import org.aya.concrete.resolve.module.ModuleLoader;
import org.aya.concrete.stmt.QualifiedID;
import org.aya.util.FileUtil;
import org.aya.util.MutableGraph;
import org.aya.util.StringUtil;
import org.aya.util.tyck.OrgaTycker;
import org.aya.util.tyck.SCCTycker;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author kiva
 */
public class LibraryCompiler {
  private final @NotNull LibraryOwner owner;
  private final @NotNull CachedModuleLoader<LibraryModuleLoader> moduleLoader;
  private final @NotNull CountingReporter reporter;
  private final @NotNull CompilerFlags flags;

  private LibraryCompiler(@NotNull CompilerFlags flags, @NotNull LibraryOwner owner, @NotNull LibraryModuleLoader.United states) {
    this.moduleLoader = new CachedModuleLoader<>(new LibraryModuleLoader(owner, states));
    this.reporter = owner.reporter();
    this.flags = flags;
    this.owner = owner;
  }

  public static @NotNull LibraryCompiler newCompiler(@NotNull CompilerFlags flags, @NotNull LibraryOwner owner) throws IOException {
    return new LibraryCompiler(flags, owner, new LibraryModuleLoader.United());
  }

  public static @NotNull LibraryCompiler newCompiler(@NotNull Reporter reporter, @NotNull CompilerFlags flags, @NotNull Path libraryRoot) throws IOException {
    var config = LibraryConfigData.fromLibraryRoot(FileUtil.canonicalize(libraryRoot));
    var owner = DiskLibraryOwner.from(reporter, config);
    return newCompiler(flags, owner);
  }

  public static int compile(@NotNull Reporter reporter, @NotNull CompilerFlags flags, @NotNull Path libraryRoot) throws IOException {
    if (!Files.exists(libraryRoot)) {
      reporter.reportString("Specified library root does not exist: " + libraryRoot);
      return 1;
    }
    return newCompiler(reporter, flags, libraryRoot).start();
  }

  private void resolveImports(@NotNull LibrarySource source) throws IOException {
    if (source.program().value != null) return; // already parsed
    var owner = source.owner();
    var program = AyaParsing.program(owner.locator(), owner.reporter(), source.file());
    source.program().value = program;
    var finder = new ImportResolver(mod -> {
      var file = owner.findModule(mod);
      if (file == null) throw new IllegalStateException("no library owns module: " + mod);
      try {
        resolveImports(file);
      } catch (IOException e) {
        throw new RuntimeException("Cannot load imported module " + mod, e);
      }
      return file;
    }, source);
    finder.resolveStmt(program);
  }

  private @NotNull MutableGraph<LibrarySource> resolveLibraryImports() throws IOException {
    var graph = MutableGraph.<LibrarySource>create();
    reportNest("[Info] Resolving source file dependency");
    var startTime = System.currentTimeMillis();
    for (var file : owner.librarySourceFiles()) {
      resolveImports(file);
      collectDep(graph, file);
    }
    reporter.reportNest("Done in " + StringUtil.timeToString(
      System.currentTimeMillis() - startTime), LibraryOwner.DEFAULT_INDENT + 2);
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
    var library = owner.underlyingLibrary();
    var anyDepChanged = false;
    for (var dep : owner.libraryDeps()) {
      var depCompiler = new LibraryCompiler(flags, dep, moduleLoader.loader.states());
      var upToDate = depCompiler.make();
      anyDepChanged = anyDepChanged || !upToDate;
      owner.registerModulePath(dep.outDir());
    }

    reporter.reportString("Compiling " + library.name());
    var startTime = System.currentTimeMillis();
    if (anyDepChanged || flags.remake()) cleanReused();

    var srcRoot = library.librarySrcRoot();
    owner.registerModulePath(srcRoot);

    var depGraph = resolveLibraryImports();
    var make = make(depGraph);
    reporter.reportNest("Library loaded in " + StringUtil.timeToString(
      System.currentTimeMillis() - startTime), LibraryOwner.DEFAULT_INDENT + 2);
    return make;
  }

  private void cleanReused() throws IOException {
    owner.librarySourceFiles().forEach(src -> {
      src.program().value = null;
      src.tycked().value = null;
      src.resolveInfo().value = null;
    });
    FileUtil.deleteRecursively(owner.underlyingLibrary().libraryOutRoot());
  }

  /**
   * @return whether the library is up-to-date.
   */
  private boolean make(@NotNull MutableGraph<LibrarySource> depGraph) throws IOException {
    var changed = buildIncremental(depGraph);
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

    Files.createDirectories(owner.outDir());
    var delayedReporter = new DelayedReporter(reporter);
    var tycker = new LibraryOrgaTycker(new LibrarySccTycker(delayedReporter, moduleLoader), changed);
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

  private @NotNull MutableGraph<LibrarySource> buildIncremental(@NotNull MutableGraph<LibrarySource> depGraph) {
    var usage = depGraph.transpose();
    var changed = MutableGraph.<LibrarySource>create();
    depGraph.E().keysView().forEach(s -> {
      if (Timestamp.sourceModified(s))
        collectChanged(usage, s, changed);
    });
    return changed;
  }

  record LibraryOrgaTycker(
    @NotNull LibrarySccTycker sccTycker,
    @NotNull MutableGraph<LibrarySource> usageGraph,
    @NotNull MutableSet<LibrarySource> skippedSet
  ) implements OrgaTycker<LibrarySource, IOException> {
    public LibraryOrgaTycker(@NotNull LibrarySccTycker sccTycker, @NotNull MutableGraph<LibrarySource> usage) {
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
      var reporter = CountingReporter.delegate(outerReporter);
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
        QualifiedID.join(mod.thisModule().moduleName()), file.displayPath()), LibraryOwner.DEFAULT_INDENT);
    }
  }

  private void reportNest(@NotNull String text) {
    reporter.reportNest(text, LibraryOwner.DEFAULT_INDENT);
  }

  private static void collectDep(@NotNull MutableGraph<LibrarySource> dep, @NotNull LibrarySource info) {
    dep.suc(info).appendAll(info.imports());
    info.imports().forEach(i -> collectDep(dep, i));
  }

  public @NotNull LibraryOwner libraryOwner() {
    return owner;
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
}
