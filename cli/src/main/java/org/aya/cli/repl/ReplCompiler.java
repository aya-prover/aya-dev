// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import kala.function.CheckedFunction;
import kala.value.MutableValue;
import org.aya.cli.library.LibraryCompiler;
import org.aya.cli.library.incremental.CompilerAdvisor;
import org.aya.cli.library.json.LibraryConfigData;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.parse.AyaParserImpl;
import org.aya.cli.single.CompilerFlags;
import org.aya.concrete.Expr;
import org.aya.concrete.desugar.AyaBinOpSet;
import org.aya.concrete.desugar.Desugarer;
import org.aya.concrete.stmt.Stmt;
import org.aya.core.def.FnDef;
import org.aya.core.def.GenericDef;
import org.aya.core.def.PrimDef;
import org.aya.core.term.Term;
import org.aya.generic.util.InterruptException;
import org.aya.generic.util.NormalizeMode;
import org.aya.ref.DefVar;
import org.aya.resolve.ModuleCallback;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.EmptyContext;
import org.aya.resolve.context.PhysicalModuleContext;
import org.aya.resolve.module.CachedModuleLoader;
import org.aya.resolve.module.FileModuleLoader;
import org.aya.resolve.module.ModuleListLoader;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.TyckState;
import org.aya.util.error.SourceFileLocator;
import org.aya.util.error.SourcePos;
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
  final @NotNull CountingReporter reporter;
  private final @NotNull SourceFileLocator locator;
  private final @NotNull CachedModuleLoader<ModuleListLoader> loader;
  private final @NotNull ReplContext context;
  private final @NotNull ImmutableSeq<Path> modulePaths;
  private final @NotNull PrimDef.Factory primFactory;
  private final @NotNull ReplShapeFactory shapeFactory;
  private final @NotNull AyaBinOpSet opSet;

  public ReplCompiler(@NotNull ImmutableSeq<Path> modulePaths, @NotNull Reporter reporter, @Nullable SourceFileLocator locator) {
    this.modulePaths = modulePaths;
    this.reporter = CountingReporter.delegate(reporter);
    this.locator = locator != null ? locator : new SourceFileLocator.Module(this.modulePaths);
    this.primFactory = new PrimDef.Factory();
    this.shapeFactory = new ReplShapeFactory();
    this.opSet = new AyaBinOpSet(this.reporter);
    this.context = new ReplContext(new EmptyContext(this.reporter, Path.of("REPL")), ImmutableSeq.of("REPL"));
    this.loader = new CachedModuleLoader<>(new ModuleListLoader(this.reporter, this.modulePaths.map(path ->
      new FileModuleLoader(this.locator, path, this.reporter, new AyaParserImpl(this.reporter), primFactory, null))));
  }

  private @NotNull ExprTycker.Result tyckExpr(@NotNull Expr expr) {
    var resolvedExpr = expr.resolve(context);
    // in case we have un-messaged TyckException
    try (var delayedReporter = new DelayedReporter(reporter)) {
      var tycker = new ExprTycker(primFactory, shapeFactory, delayedReporter, null);
      var desugar = desugarExpr(resolvedExpr, delayedReporter);
      return tycker.zonk(tycker.synthesize(desugar));
    }
  }

  private @NotNull Expr desugarExpr(@NotNull Expr expr, @NotNull Reporter reporter) {
    var resolveInfo = new ResolveInfo(primFactory, shapeFactory, opSet,
      new EmptyContext(reporter, Path.of("dummy")).derive("dummy"),
      ImmutableSeq.empty());
    return new Desugarer(resolveInfo).apply(expr);
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
      .forEach(mod -> mod.exports.forEach((name, contents) -> context.importModule(
        Stmt.Accessibility.Public,
        SourcePos.NONE,
        name,
        contents
      )));
    owner.libraryDeps().forEach(this::importModule);
  }

  /** @see org.aya.cli.single.SingleFileCompiler#compile(Path, Function, CompilerFlags, ModuleCallback) */
  private void loadFile(@NotNull Path file) {
    compileToContext(parser -> Either.left(parser.program(locator, file)), NormalizeMode.WHNF);
  }

  /** @param text the text of code to compile, witch might either be a `program` or an `expr`. */
  public @NotNull Either<ImmutableSeq<GenericDef>, Term> compileToContext(@NotNull String text, @NotNull NormalizeMode normalizeMode) {
    if (text.isBlank()) return Either.left(ImmutableSeq.empty());
    return compileToContext(parser -> parser.repl(text), normalizeMode);
  }

  /**
   * Copied and adapted.
   *
   * @see org.aya.cli.single.SingleFileCompiler#compile
   */
  public @NotNull Either<ImmutableSeq<GenericDef>, Term> compileToContext(
    @NotNull CheckedFunction<AyaParserImpl, Either<ImmutableSeq<Stmt>, Expr>, IOException> parsing,
    @NotNull NormalizeMode normalizeMode
  ) {
    try {
      var parser = new AyaParserImpl(reporter);
      var programOrExpr = parsing.apply(parser);
      return programOrExpr.map(
        program -> {
          var newDefs = MutableValue.<ImmutableSeq<GenericDef>>create();
          var resolveInfo = loader.resolveModule(primFactory, shapeFactory, opSet, context.fork(), program, loader);
          resolveInfo.shapeFactory().discovered = shapeFactory.fork().discovered;
          loader.tyckModule(null, resolveInfo, ((moduleResolve, defs) -> newDefs.set(defs)));
          if (reporter.anyError()) return ImmutableSeq.empty();
          context.merge();
          shapeFactory.merge();
          return newDefs.get();
        },
        expr -> tyckExpr(expr).wellTyped().normalize(new TyckState(primFactory), normalizeMode)
      );
    } catch (InterruptException ignored) {
      // Only two kinds of interruptions are possible: parsing and resolving
      return Either.left(ImmutableSeq.empty());
    }
  }

  public @Nullable Term computeType(@NotNull String text, @NotNull NormalizeMode normalizeMode) {
    try {
      var parseTree = new AyaParserImpl(reporter).repl(text);
      if (parseTree.isLeft()) {
        reporter.reportString("Expect expression, got statement", Problem.Severity.ERROR);
        return null;
      }
      return tyckExpr(parseTree.getRightValue()).type().normalize(new TyckState(primFactory), normalizeMode);
    } catch (InterruptException ignored) {
      return null;
    }
  }

  public @Nullable FnDef codificationObject(@NotNull String text) {
    var parseTree = new AyaParserImpl(reporter).expr(text, SourcePos.NONE);
    if (parseTree.resolve(context) instanceof Expr.Ref ref
      && ref.resolvedVar() instanceof DefVar<?, ?> defVar
      && defVar.core instanceof FnDef fn
      && fn.body.isLeft()) {
      return fn;
    }
    System.out.println(parseTree);
    return null;
  }

  public @NotNull ReplContext getContext() {
    return context;
  }
}
