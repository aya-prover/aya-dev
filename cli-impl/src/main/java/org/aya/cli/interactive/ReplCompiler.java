// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.interactive;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import kala.function.CheckedFunction;
import kala.value.MutableValue;
import org.aya.cli.library.LibraryCompiler;
import org.aya.cli.library.incremental.CompilerAdvisor;
import org.aya.cli.library.json.LibraryConfigData;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.single.CompilerFlags;
import org.aya.cli.single.SingleAyaFile;
import org.aya.cli.utils.LiterateData;
import org.aya.generic.InterruptException;
import org.aya.normalize.Normalizer;
import org.aya.primitive.PrimFactory;
import org.aya.primitive.ShapeFactory;
import org.aya.producer.AyaParserImpl;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.EmptyContext;
import org.aya.resolve.context.PhysicalModuleContext;
import org.aya.resolve.module.CachedModuleLoader;
import org.aya.resolve.module.FileModuleLoader;
import org.aya.resolve.module.ModuleCallback;
import org.aya.resolve.module.ModuleListLoader;
import org.aya.resolve.salt.AyaBinOpSet;
import org.aya.resolve.salt.Desalt;
import org.aya.resolve.visitor.ExprResolver;
import org.aya.syntax.GenericAyaFile;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.literate.CodeOptions.NormalizeMode;
import org.aya.syntax.ref.AnyVar;
import org.aya.syntax.ref.ModulePath;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.Jdg;
import org.aya.tyck.TyckState;
import org.aya.tyck.tycker.TeleTycker;
import org.aya.util.error.SourceFileLocator;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.CountingReporter;
import org.aya.util.reporter.DelayedReporter;
import org.aya.util.reporter.Problem;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReplCompiler {
  public final @NotNull CountingReporter reporter;
  public final @NotNull ImmutableSeq<Path> modulePaths;
  private final @NotNull SourceFileLocator locator;
  private final @NotNull CachedModuleLoader<ModuleListLoader> loader;
  private final @NotNull ReplContext context;
  private final @NotNull PrimFactory primFactory;
  private final @NotNull ReplShapeFactory shapeFactory;
  private final @NotNull GenericAyaFile.Factory fileManager;
  private final @NotNull AyaBinOpSet opSet;
  private final @NotNull TyckState tcState;

  public ReplCompiler(
    @NotNull ImmutableSeq<Path> modulePaths,
    @NotNull Reporter delegateReporter,
    @Nullable SourceFileLocator locator
  ) {
    this.modulePaths = modulePaths;
    reporter = CountingReporter.delegate(delegateReporter);
    this.locator = locator != null ? locator : new SourceFileLocator.Module(this.modulePaths);
    this.primFactory = new PrimFactory() {
      @Override public boolean suppressRedefinition() { return true; }
    };
    this.shapeFactory = new ReplShapeFactory();
    this.opSet = new AyaBinOpSet(reporter);
    this.context = new ReplContext(new EmptyContext(reporter, Path.of("REPL")), ModulePath.of("REPL"));
    this.fileManager = new SingleAyaFile.Factory(reporter);
    var parser = new AyaParserImpl(reporter);
    this.loader = new CachedModuleLoader<>(new ModuleListLoader(reporter, this.modulePaths.map(path ->
      new FileModuleLoader(this.locator, path, reporter, parser, fileManager, primFactory))));
    tcState = new TyckState(shapeFactory, primFactory);
  }

  private @NotNull ExprResolver.LiterateResolved
  desugarExpr(@NotNull ExprResolver.LiterateResolved expr, @NotNull Reporter reporter) {
    var ctx = new EmptyContext(reporter, Path.of("dummy")).derive("dummy");
    var resolveInfo = new ResolveInfo(ctx, primFactory, shapeFactory, opSet);
    return expr.descent(new Desalt(resolveInfo));
  }

  public void loadToContext(@NotNull Path file) throws IOException {
    if (Files.isDirectory(file)) loadLibrary(file);
    else loadFile(file);
  }

  private void loadLibrary(@NotNull Path libraryRoot) throws IOException {
    var flags = new CompilerFlags(CompilerFlags.Message.EMOJI, false, true, null, modulePaths.view(), null);
    try {
      var compiler = LibraryCompiler.newCompiler(primFactory, reporter, flags, CompilerAdvisor.onDisk(), libraryRoot);
      compiler.start();
      var owner = compiler.libraryOwner();
      importModule(owner);
    } catch (LibraryConfigData.BadConfig bad) {
      reporter.reportString("Cannot load malformed library: " + bad.getMessage(), Problem.Severity.ERROR);
    }
  }

  private void importModule(@NotNull LibraryOwner owner) {
    owner.librarySources()
      .map(src -> src.resolveInfo().get().thisModule())
      .filterIsInstance(PhysicalModuleContext.class)
      .forEach(mod -> context.importModuleContext(mod.modulePath().asName(), mod, Stmt.Accessibility.Public, SourcePos.NONE));
    owner.libraryDeps().forEach(this::importModule);
  }

  /** @see org.aya.cli.single.SingleFileCompiler#compile(Path, ModuleCallback) */
  private void loadFile(@NotNull Path file) {
    compileToContext(parser -> Either.left(fileManager.createAyaFile(locator, file).parseMe(parser)), NormalizeMode.HEAD);
  }

  /** @param text the text of code to compile, witch might either be a `program` or an `expr`. */
  public @NotNull Either<ImmutableSeq<TyckDef>, Term> compileToContext(@NotNull String text, @NotNull NormalizeMode normalizeMode) {
    if (text.isBlank()) return Either.left(ImmutableSeq.empty());
    return compileToContext(parser -> parser.repl(text), normalizeMode);
  }

  /**
   * Copied and adapted.
   *
   * @see org.aya.cli.single.SingleFileCompiler#compile
   */
  public @NotNull Either<ImmutableSeq<TyckDef>, Term> compileToContext(
    @NotNull CheckedFunction<AyaParserImpl, Either<ImmutableSeq<Stmt>, WithPos<Expr>>, IOException> parsing,
    @NotNull NormalizeMode normalizeMode
  ) {
    try {
      var parser = new AyaParserImpl(reporter);
      var programOrExpr = parsing.applyChecked(parser);
      return programOrExpr.map(
        program -> {
          var newDefs = MutableValue.<ImmutableSeq<TyckDef>>create();
          var resolveInfo = new ResolveInfo(context.fork(), primFactory, shapeFactory, opSet);
          loader.resolveModule(resolveInfo, program, loader);
          resolveInfo.shapeFactory().discovered = shapeFactory.fork().discovered;
          loader.tyckModule(resolveInfo, ((_, defs) -> newDefs.set(defs)));
          if (reporter.anyError()) return ImmutableSeq.empty();
          context.merge();
          shapeFactory.merge();
          return newDefs.get();
        },
        expr -> tyckAndNormalize(expr, false, normalizeMode)
      );
    } catch (InterruptException _) {
      // Only two kinds of interruptions are possible: parsing and resolving
      return Either.left(ImmutableSeq.empty());
    } catch (IOException e) {
      reporter.reportString(e.getMessage());
      return Either.left(ImmutableSeq.empty());
    }
  }

  public @Nullable AnyVar parseToAnyVar(@NotNull String text) {
    var parseTree = parseExpr(text);
    if (parseTree == null) return null;
    try {
      if (ExprResolver.resolveLax(context, parseTree).expr().data() instanceof Expr.Ref unresolved)
        return unresolved.var();
    } catch (InterruptException ignored) {
    }
    return null;
  }

  public @Nullable Term computeType(@NotNull String text, NormalizeMode mode) {
    try {
      var expr = parseExpr(text);
      if (expr == null) return null;
      return tyckAndNormalize(expr, true, mode);
    } catch (InterruptException ignored) {
      return null;
    }
  }
  private @Nullable WithPos<Expr> parseExpr(@NotNull String text) {
    var parseTree = new AyaParserImpl(reporter).repl(text);
    if (parseTree.isLeft()) {
      reporter.reportString("Expect expression, got statement", Problem.Severity.ERROR);
      return null;
    }
    return parseTree.getRightValue();
  }

  /** @param isType true means take the type, otherwise take the term. */
  private @NotNull Term tyckAndNormalize(WithPos<Expr> expr, boolean isType, NormalizeMode mode) {
    Jdg jdg = null;
    var resolvedExpr = ExprResolver.resolveLax(context, expr);
    if (mode == NormalizeMode.NULL) jdg = LiterateData.simpleVar(resolvedExpr.expr().data());
    // in case we have un-messaged TyckException
    if (jdg == null) try (var delayedReporter = new DelayedReporter(reporter)) {
      tcState.clearTmp();
      var desugar = desugarExpr(resolvedExpr, delayedReporter);
      var tycker = new TeleTycker.InlineCode(new ExprTycker(tcState, delayedReporter));
      jdg = tycker.checkInlineCode(desugar.params(), desugar.expr());
    }
    return new Normalizer(tcState).normalize(isType ? jdg.type() : jdg.wellTyped(), mode);
  }

  public @NotNull ReplContext getContext() { return context; }
  public @NotNull ShapeFactory getShapeFactory() { return shapeFactory; }
  public void loadPreludeIfPossible() {
    if (loader.existsFileLevelModule(ModulePath.of("prelude"))) {
      compileToContext("open import prelude", NormalizeMode.NULL);
    }
  }
}
