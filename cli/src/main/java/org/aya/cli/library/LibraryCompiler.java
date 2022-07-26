// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableSet;
import org.aya.cli.library.incremental.CompilerAdvisor;
import org.aya.cli.library.json.LibraryConfigData;
import org.aya.cli.library.source.DiskLibraryOwner;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.cli.parse.AyaParserImpl;
import org.aya.cli.single.CompilerFlags;
import org.aya.cli.utils.AyaCompiler;
import org.aya.concrete.stmt.QualifiedID;
import org.aya.core.def.PrimDef;
import org.aya.generic.util.InternalException;
import org.aya.resolve.context.Context;
import org.aya.resolve.error.ModNotFoundError;
import org.aya.resolve.module.CachedModuleLoader;
import org.aya.resolve.module.ModuleLoader;
import org.aya.util.FileUtil;
import org.aya.util.MutableGraph;
import org.aya.util.StringUtil;
import org.aya.util.reporter.CountingReporter;
import org.aya.util.reporter.Reporter;
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
  private final @NotNull CompilerAdvisor advisor;

  private LibraryCompiler(@NotNull Reporter reporter, @NotNull CompilerFlags flags, @NotNull LibraryOwner owner, @NotNull CompilerAdvisor advisor, @NotNull LibraryModuleLoader.United states) {
    var counting = CountingReporter.delegate(reporter);
    this.advisor = advisor;
    this.moduleLoader = new CachedModuleLoader<>(new LibraryModuleLoader(counting, owner, advisor, states));
    this.reporter = counting;
    this.flags = flags;
    this.owner = owner;
  }

  public static @NotNull LibraryCompiler newCompiler(
    @NotNull PrimDef.Factory primFactory,
    @NotNull Reporter reporter,
    @NotNull CompilerFlags flags,
    @NotNull CompilerAdvisor advisor,
    @NotNull LibraryOwner owner
  ) {
    return new LibraryCompiler(reporter, flags, owner, advisor, new LibraryModuleLoader.United(primFactory));
  }

  public static @NotNull LibraryCompiler newCompiler(
    @NotNull PrimDef.Factory primFactory,
    @NotNull Reporter reporter,
    @NotNull CompilerFlags flags,
    @NotNull CompilerAdvisor advisor,
    @NotNull Path libraryRoot
  ) throws IOException {
    var config = LibraryConfigData.fromLibraryRoot(libraryRoot);
    var owner = DiskLibraryOwner.from(config);
    return newCompiler(primFactory, reporter, flags, advisor, owner);
  }

  public static int compile(
    @NotNull PrimDef.Factory primFactory,
    @NotNull Reporter reporter,
    @NotNull CompilerFlags flags,
    @NotNull CompilerAdvisor advisor,
    @NotNull Path libraryRoot
  ) throws IOException {
    if (!Files.exists(libraryRoot)) {
      reporter.reportString("Specified library root does not exist: " + libraryRoot);
      return 1;
    }
    return newCompiler(primFactory, reporter, flags, advisor, libraryRoot).start();
  }

  private void resolveImports(@NotNull LibrarySource source) throws IOException {
    if (source.program().get() != null) return; // already parsed
    var owner = source.owner();
    var program = new AyaParserImpl(reporter).program(owner.locator(), source.file());
    source.program().set(program);
    var finder = new ImportResolver((mod, sourcePos) -> {
      var file = owner.findModule(mod);
      if (file == null) {
        reporter.report(new ModNotFoundError(mod, sourcePos));
        throw new Context.ResolvingInterruptedException();
      }
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
    for (var file : owner.librarySources()) {
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
      var depCompiler = new LibraryCompiler(reporter, flags, dep, advisor, moduleLoader.loader.states());
      var upToDate = depCompiler.make();
      anyDepChanged = anyDepChanged || !upToDate;
      owner.addModulePath(dep.outDir());
    }

    reporter.reportString("Compiling " + library.name());
    var startTime = System.currentTimeMillis();
    if (anyDepChanged || flags.remake()) cleanReused();

    var srcRoot = library.librarySrcRoot();
    owner.addModulePath(srcRoot);

    var depGraph = resolveLibraryImports();
    var make = make(depGraph);
    reporter.reportNest("Library loaded in " + StringUtil.timeToString(
      System.currentTimeMillis() - startTime), LibraryOwner.DEFAULT_INDENT + 2);
    return make;
  }

  private void cleanReused() throws IOException {
    owner.librarySources().forEach(src -> {
      src.program().set(null);
      src.tycked().set(null);
      src.resolveInfo().set(null);
    });
    FileUtil.deleteRecursively(owner.outDir());
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

    advisor.prepareLibraryOutput(owner);
    advisor.notifyIncrementalJob(SCCs);

    var tycker = new LibraryOrgaTycker(new LibrarySccTycker(reporter, moduleLoader, advisor), changed);
    SCCs.forEachChecked(tycker::tyckSCC);
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
      if (advisor.isSourceModified(s))
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
    @NotNull CountingReporter reporter,
    @NotNull ModuleLoader moduleLoader,
    @NotNull CompilerAdvisor advisor
  ) implements SCCTycker<LibrarySource, IOException> {
    @Override
    public @NotNull ImmutableSeq<LibrarySource> tyckSCC(@NotNull ImmutableSeq<LibrarySource> order) throws IOException {
      for (var f : order) advisor.prepareModuleOutput(f);
      for (var f : order) {
        tyckOne(f);
        if (reporter.anyError()) {
          reporter.clear();
          return ImmutableSeq.of(f);
        }
      }
      return ImmutableSeq.empty();
    }

    private void tyckOne(@NotNull LibrarySource file) {
      var moduleName = file.moduleName();
      var mod = moduleLoader.load(moduleName);
      if (mod == null || file.resolveInfo().get() == null)
        throw new InternalException("Unable to load module: " + moduleName);
      reporter.reportNest("[Tyck] %s (%s)".formatted(
        QualifiedID.join(mod.thisModule().moduleName()), file.displayPath()), LibraryOwner.DEFAULT_INDENT);
    }
  }

  private void reportNest(@NotNull String text) {
    reporter.reportNest(text, LibraryOwner.DEFAULT_INDENT);
  }

  private static void collectDep(@NotNull MutableGraph<LibrarySource> dep, @NotNull LibrarySource info) {
    dep.sucMut(info).appendAll(info.imports());
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
    var suc = usage.suc(changed);
    changedGraph.sucMut(changed).appendAll(suc);
    suc.forEach(dep -> collectChanged(usage, dep, changedGraph));
  }
}
