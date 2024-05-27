// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.interactive;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import kala.function.CheckedFunction;
import kala.value.MutableValue;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.single.CompilerFlags;
import org.aya.cli.single.SingleAyaFile;
import org.aya.generic.InterruptException;
import org.aya.normalize.Normalizer;
import org.aya.primitive.PrimFactory;
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
import org.aya.syntax.ref.ModulePath;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.Jdg;
import org.aya.tyck.TyckState;
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
import java.util.function.Function;

public class ReplCompiler {
  public final @NotNull CountingReporter reporter;
  private final @NotNull SourceFileLocator locator;
  private final @NotNull CachedModuleLoader<ModuleListLoader> loader;
  private final @NotNull ReplContext context;
  private final @NotNull ImmutableSeq<Path> modulePaths;
  private final @NotNull PrimFactory primFactory;
  private final @NotNull ReplShapeFactory shapeFactory;
  private final @NotNull GenericAyaFile.Factory fileManager;
  private final @NotNull AyaBinOpSet opSet;
  private final @NotNull TyckState tcState;

  public ReplCompiler(@NotNull ImmutableSeq<Path> modulePaths, @NotNull Reporter reporter, @Nullable SourceFileLocator locator) {
    this.modulePaths = modulePaths;
    this.reporter = CountingReporter.delegate(reporter);
    this.locator = locator != null ? locator : new SourceFileLocator.Module(this.modulePaths);
    this.primFactory = new PrimFactory() {
      @Override public boolean suppressRedefinition() { return true; }
    };
    this.shapeFactory = new ReplShapeFactory();
    this.opSet = new AyaBinOpSet(this.reporter);
    this.context = new ReplContext(new EmptyContext(this.reporter, Path.of("REPL")), ModulePath.of("REPL"));
    this.fileManager = new SingleAyaFile.Factory(this.reporter);
    var parser = new AyaParserImpl(this.reporter);
    this.loader = new CachedModuleLoader<>(new ModuleListLoader(this.reporter, this.modulePaths.map(path ->
      new FileModuleLoader(this.locator, path, this.reporter, parser, fileManager, primFactory))));
    tcState = new TyckState(shapeFactory, primFactory);
  }

  private @NotNull Jdg tyckExpr(@NotNull WithPos<Expr> expr) {
    var resolvedExpr = ExprResolver.resolveLax(context, expr);
    // in case we have un-messaged TyckException
    try (var delayedReporter = new DelayedReporter(reporter)) {
      var tycker = new ExprTycker(tcState, delayedReporter);
      var desugar = desugarExpr(resolvedExpr, delayedReporter);
      return tycker.zonk(tycker.synthesize(desugar));
    }
  }

  private @NotNull WithPos<Expr> desugarExpr(@NotNull WithPos<Expr> expr, @NotNull Reporter reporter) {
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
    throw new UnsupportedOperationException("TODO");
    // try {
    //   var compiler = LibraryCompiler.newCompiler(primFactory, reporter, flags, CompilerAdvisor.onDisk(), libraryRoot);
    //   compiler.start();
    //   var owner = compiler.libraryOwner();
    //   importModule(owner);
    // } catch (LibraryConfigData.BadConfig bad) {
    //   reporter.reportString("Cannot load malformed library: " + bad.getMessage(), Problem.Severity.ERROR);
    // }
  }

  private void importModule(@NotNull LibraryOwner owner) {
    owner.librarySources()
      .map(src -> src.resolveInfo().get().thisModule())
      .filterIsInstance(PhysicalModuleContext.class)
      .forEach(mod -> context.importModule(mod.modulePath().asName(), mod, Stmt.Accessibility.Public, SourcePos.NONE));
    owner.libraryDeps().forEach(this::importModule);
  }

  /** @see org.aya.cli.single.SingleFileCompiler#compile(Path, Function, CompilerFlags, ModuleCallback) */
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
      var programOrExpr = parsing.apply(parser);
      return programOrExpr.map(
        program -> {
          var newDefs = MutableValue.<ImmutableSeq<TyckDef>>create();
          var resolveInfo = loader.resolveModule(primFactory, shapeFactory, opSet, context.fork(), program, loader);
          resolveInfo.shapeFactory().discovered = shapeFactory.fork().discovered;
          loader.tyckModule(resolveInfo, ((_, defs) -> newDefs.set(defs)));
          if (reporter.anyError()) return ImmutableSeq.empty();
          context.merge();
          shapeFactory.merge();
          return newDefs.get();
        },
        expr -> new Normalizer(tcState).normalize(tyckExpr(expr).wellTyped(), normalizeMode)
      );
    } catch (InterruptException _) {
      // Only two kinds of interruptions are possible: parsing and resolving
      return Either.left(ImmutableSeq.empty());
    }
  }

  public @Nullable Term computeType(@NotNull String text, NormalizeMode mode) {
    try {
      var parseTree = new AyaParserImpl(reporter).repl(text);
      if (parseTree.isLeft()) {
        reporter.reportString("Expect expression, got statement", Problem.Severity.ERROR);
        return null;
      }
      return new Normalizer(tcState).normalize(tyckExpr(parseTree.getRightValue()).type(), mode);
    } catch (InterruptException ignored) {
      return null;
    }
  }

  public @NotNull ReplContext getContext() {
    return context;
  }
}
