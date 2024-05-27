// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableSet;
import org.aya.cli.library.incremental.CompilerAdvisor;
import org.aya.cli.library.json.LibraryConfigData;
import org.aya.cli.library.source.DiskLibraryOwner;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.cli.render.RenderOptions;
import org.aya.cli.single.CompilerFlags;
import org.aya.cli.utils.CliEnums;
import org.aya.cli.utils.CompilerUtil;
import org.aya.generic.InterruptException;
import org.aya.pretty.backend.string.StringPrinterConfig;
import org.aya.pretty.printer.PrinterConfig;
import org.aya.primitive.PrimFactory;
import org.aya.resolve.context.Context;
import org.aya.resolve.error.NameProblem;
import org.aya.resolve.module.CachedModuleLoader;
import org.aya.resolve.module.ModuleLoader;
import org.aya.syntax.AyaFiles;
import org.aya.syntax.concrete.stmt.Command;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.concrete.stmt.decl.PrimDecl;
import org.aya.util.error.Panic;
import org.aya.util.more.StringUtil;
import org.aya.util.reporter.CountingReporter;
import org.aya.util.reporter.Reporter;
import org.aya.util.terck.MutableGraph;
import org.aya.util.tyck.OrgaTycker;
import org.aya.util.tyck.SCCTycker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    @NotNull PrimFactory primFactory,
    @NotNull Reporter reporter,
    @NotNull CompilerFlags flags,
    @NotNull CompilerAdvisor advisor,
    @NotNull LibraryOwner owner
  ) {
    return new LibraryCompiler(reporter, flags, owner, advisor, new LibraryModuleLoader.United(primFactory));
  }

  public static @NotNull LibraryCompiler newCompiler(
    @NotNull PrimFactory primFactory,
    @NotNull Reporter reporter,
    @NotNull CompilerFlags flags,
    @NotNull CompilerAdvisor advisor,
    @NotNull Path libraryRoot
  ) throws IOException, LibraryConfigData.BadConfig {
    var config = LibraryConfigData.fromLibraryRoot(libraryRoot);
    var owner = DiskLibraryOwner.from(config);
    return newCompiler(primFactory, reporter, flags, advisor, owner);
  }

  public static int compile(
    @NotNull PrimFactory primFactory,
    @NotNull Reporter reporter,
    @NotNull CompilerFlags flags,
    @NotNull CompilerAdvisor advisor,
    @NotNull Path libraryRoot
  ) throws IOException {
    if (!Files.exists(libraryRoot)) {
      reporter.reportString(STR."Specified library root does not exist: \{libraryRoot}");
      return 1;
    }
    try {
      return newCompiler(primFactory, reporter, flags, advisor, libraryRoot).start();
    } catch (LibraryConfigData.BadConfig bad) {
      reporter.reportString(STR."Cannot load malformed library: \{bad.getMessage()}");
      return 1;
    }
  }

  private void parse(@NotNull LibrarySource source) throws IOException {
    source.parseMe(advisor.createParser(reporter));
  }

  /** @return whether the source file is already parsed. */
  private boolean parseIfNeeded(@NotNull LibrarySource source) throws IOException {
    if (source.program().get() != null) return true; // already parsed
    parse(source);
    return false;
  }

  /**
   * Traverse the source file's import statements and build its dependency graph.
   * The graph is used to generate incremental build list according to files'
   * last modified time.
   */
  private void resolveImportsIfNeeded(@NotNull LibrarySource source) throws IOException {
    if (parseIfNeeded(source)) return; // already resolved
    var finder = new ImportResolver((mod, sourcePos) -> {
      var recurse = owner.findModule(mod);
      if (recurse == null) {
        reporter.report(new NameProblem.ModNotFoundError(mod, sourcePos));
        throw new Context.ResolvingInterruptedException();
      }
      return recurse;
    }, source);
    finder.resolveStmt(source.program().get());
  }

  private @NotNull MutableGraph<LibrarySource> resolveImports() throws IOException {
    var depGraph = MutableGraph.<LibrarySource>create();
    reportNest("[Info] Resolving source file dependency");
    var startTime = System.currentTimeMillis();
    owner.librarySources().forEachChecked(src -> {
      resolveImportsIfNeeded(src);
      var known = depGraph.sucMut(src);
      var dedup = src.imports().filter(s ->
        known.noneMatch(k -> k.moduleName().equals(s.moduleName())));
      known.appendAll(dedup);
    });
    reporter.reportNest(STR."Done in \{StringUtil.timeToString(
      System.currentTimeMillis() - startTime)}", LibraryOwner.DEFAULT_INDENT + 2);
    return depGraph;
  }

  public int start() throws IOException {
    if (flags.modulePaths().isNotEmpty()) reporter.reportString(
      "Warning: command-line specified module path (--module-path) is ignored when compiling libraries.");
    if (flags.outputFile() != null) reporter.reportString(
      "Warning: command-line specified output file (-o, --output) is ignored when compiling libraries.");
    return CompilerUtil.catching(reporter, flags, this::make);
  }

  private void pretty(ImmutableSeq<LibrarySource> modified) throws IOException {
    var cmdPretty = flags.prettyInfo();
    if (cmdPretty == null) return;
    if (cmdPretty.prettyStage() != CliEnums.PrettyStage.literate) {
      reporter.reportString("Warning: only 'literate' pretty stage is supported when compiling libraries.");
      return;
    }

    // prepare literate output path
    reportNest("[Info] Generating literate output");
    var litConfig = owner.underlyingLibrary().literateConfig();
    var outputDir = cmdPretty.prettyDir() != null
      ? Files.createDirectories(Path.of(cmdPretty.prettyDir()))
      : Files.createDirectories(litConfig.outputPath());

    // If the library specifies no literate options, use the ones from the command line.
    var litPretty = litConfig.pretty();
    var prettierOptions = litPretty != null ? litPretty.prettierOptions : cmdPretty.prettierOptions();
    var renderOptions = litPretty != null ? litPretty.renderOptions : cmdPretty.renderOptions();
    // always use the backend options from the command line, like output format, server-side rendering, etc.
    var outputTarget = cmdPretty.prettyFormat().target;
    var setup = cmdPretty.backendOpts(true).then(new RenderOptions.BackendSetup() {
      @Override public <T extends PrinterConfig.Basic<?>> @NotNull T setup(@NotNull T config) {
        config.set(StringPrinterConfig.LinkOptions.CrossLinkPrefix, litConfig.linkPrefix());
        config.set(StringPrinterConfig.LinkOptions.CrossLinkSeparator, "/");
        config.set(StringPrinterConfig.LinkOptions.CrossLinkPostfix, switch (outputTarget) {
          case AyaMd, HTML -> ".html";
          default -> "";
        });
        return config;
      }
    });
    // THE BIG GAME
    modified.forEachChecked(src -> {
      // reportNest(STR."[Pretty] \{QualifiedID.join(src.moduleName())}");
      var doc = src.pretty(ImmutableSeq.empty(), prettierOptions);
      var text = renderOptions.render(outputTarget, doc, setup);
      var outputFileName = AyaFiles.stripAyaSourcePostfix(src.displayPath().toString()) + outputTarget.fileExt;
      var outputFile = outputDir.resolve(outputFileName);
      Files.createDirectories(outputFile.getParent());
      Files.writeString(outputFile, text);
    });
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

    reporter.reportString(STR."Compiling \{library.name()}");
    var startTime = System.currentTimeMillis();
    if (anyDepChanged || flags.remake()) {
      owner.librarySources().forEach(this::clearModified);
      advisor.clearLibraryOutput(owner);
    }

    var srcRoot = library.librarySrcRoot();
    owner.addModulePath(srcRoot);

    var modified = collectModified();
    if (modified.isEmpty()) {
      reportNest("[Info] No changes detected, no need to remake");
      return true;
    }

    var make = make(modified);
    reporter.reportNest(STR."Library loaded in \{StringUtil.timeToString(
      System.currentTimeMillis() - startTime)}", LibraryOwner.DEFAULT_INDENT + 2);
    pretty(modified);
    return make;
  }

  /**
   * @return whether the library is up-to-date.
   */
  private boolean make(@NotNull ImmutableSeq<LibrarySource> modified) throws IOException {
    // modified sources need reparse
    modified.forEach(this::clearModified);
    var depGraph = resolveImports();
    var affected = collectAffected(modified, depGraph);
    var SCCs = affected.topologicalOrder().view()
      .reversed().toImmutableSeq();
    // ^ top order generated from usage graph should be reversed.
    // Only here we generate top order from usage graph just for efficiency
    // (transposing a graph is slower than reversing a list).
    // in other cases we always generate top order from dependency graphs
    // because usage graphs are never needed.

    // clear some info instead of reparse? No we can't, because
    // the StmtResolver mutates the concrete tree.
    SCCs.forEachChecked(i -> i.forEachChecked(this::reparseAffected));

    advisor.prepareLibraryOutput(owner);
    advisor.notifyIncrementalJob(modified, SCCs);

    var tycker = new LibraryOrgaTycker(new LibrarySccTycker(reporter, moduleLoader, advisor), affected);
    SCCs.forEachChecked(tycker::tyckSCC);
    if (tycker.skippedSet.isNotEmpty()) {
      reporter.reportString("I dislike the following module(s):");
      tycker.skippedSet.forEach(f ->
        reportNest(String.format("%s (%s)", f.moduleName(), f.displayPath())));
      // Stop the whole compilation in case downstream libraries depend on skipped modules.
      throw new LibraryTyckingFailed();
    } else {
      reporter.reportString("I like these modules :)");
    }
    return false;
  }

  private void reparseAffected(@NotNull LibrarySource src) throws IOException {
    if (src.tycked().get() == null) return;
    src.tycked().set(null);
    src.resolveInfo().set(null);
    src.literateData().set(null);
    clearPrimitives(src.program().get());
    parse(src);
  }

  private void clearModified(@NotNull LibrarySource src) {
    clearPrimitives(src.program().get());
    src.program().set(null);
    src.tycked().set(null);
    src.resolveInfo().set(null);
    src.literateData().set(null);
    src.imports().clear();
  }

  /** collect usages of directly modified source files */
  private static @NotNull MutableGraph<LibrarySource> collectAffected(
    @NotNull ImmutableSeq<LibrarySource> modified,
    @NotNull MutableGraph<LibrarySource> depGraph
  ) {
    var usageGraph = depGraph.transpose();
    var affectedUsage = MutableGraph.<LibrarySource>create();
    modified.forEach(aff -> collectAffected(usageGraph, aff, affectedUsage));
    return affectedUsage;
  }

  private static void collectAffected(
    @NotNull MutableGraph<LibrarySource> usageGraph,
    @NotNull LibrarySource affected,
    @NotNull MutableGraph<LibrarySource> affectedUsage
  ) {
    if (affectedUsage.E().containsKey(affected)) return;
    var suc = usageGraph.suc(affected);
    affectedUsage.sucMut(affected).appendAll(suc);
    suc.forEach(dep -> collectAffected(usageGraph, dep, affectedUsage));
  }

  /** collect source files that are directly modified by user */
  private @NotNull ImmutableSeq<LibrarySource> collectModified() {
    return owner.librarySources().filter(advisor::isSourceModified).toImmutableSeq();
  }

  private void clearPrimitives(@Nullable ImmutableSeq<Stmt> stmts) {
    if (stmts == null) return;
    PrimitiveCleaner.clear(moduleLoader.loader.states().primFactory(), stmts);
  }

  interface PrimitiveCleaner {
    static void clear(@NotNull PrimFactory factory, @NotNull ImmutableSeq<Stmt> stmts) {
      stmts.forEach(s -> clear(factory, s));
    }

    static void clear(@NotNull PrimFactory factory, @NotNull Stmt stmt) {
      switch (stmt) {
        case Command.Module mod -> clear(factory, mod.contents());
        case PrimDecl decl when decl.ref.core != null -> factory.clear(decl.ref.core.id);
        default -> { }
      }
    }
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
      for (var f : order) advisor.clearModuleOutput(f);
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
      reporter.reportNest("[Tyck] %s (%s)".formatted(
        moduleName.toString(), file.displayPath()), LibraryOwner.DEFAULT_INDENT);
      var mod = moduleLoader.load(moduleName);
      if (mod == null || file.resolveInfo().get() == null)
        throw new Panic(STR."Unable to load module: \{moduleName}");
    }
  }

  public static class LibraryTyckingFailed extends InterruptException {
    @Override public InterruptStage stage() {
      return InterruptStage.Tycking;
    }
  }

  private void reportNest(@NotNull String text) { reporter.reportNest(text, LibraryOwner.DEFAULT_INDENT); }
  public @NotNull LibraryOwner libraryOwner() { return owner; }
}
