// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import kala.value.Ref;
import org.aya.cli.library.LibraryCompiler;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.parse.AyaParserImpl;
import org.aya.cli.single.CompilerFlags;
import org.aya.cli.single.SingleFileCompiler;
import org.aya.concrete.Expr;
import org.aya.concrete.desugar.AyaBinOpSet;
import org.aya.concrete.desugar.Desugarer;
import org.aya.concrete.stmt.Stmt;
import org.aya.core.def.Def;
import org.aya.core.def.PrimDef;
import org.aya.core.term.Term;
import org.aya.generic.util.InterruptException;
import org.aya.generic.util.NormalizeMode;
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
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReplCompiler {
  final @NotNull CountingReporter reporter;
  private final @Nullable SourceFileLocator locator;
  private final @NotNull ReplContext context;
  private final @NotNull ImmutableSeq<Path> modulePaths;
  private final @NotNull CompilerFlags flags;
  private final @NotNull PrimDef.Factory primFactory;

  public ReplCompiler(@NotNull ImmutableSeq<Path> modulePaths, @NotNull Reporter reporter, @Nullable SourceFileLocator locator) {
    this.modulePaths = modulePaths;
    this.reporter = CountingReporter.delegate(reporter);
    this.locator = locator;
    this.primFactory = new PrimDef.Factory();
    this.context = new ReplContext(new EmptyContext(this.reporter, Path.of("REPL")), ImmutableSeq.of("REPL"));
    this.flags = new CompilerFlags(CompilerFlags.Message.EMOJI, false, true, null,
      modulePaths.view(), null);
  }

  private @NotNull ExprTycker.Result tyckExpr(@NotNull Expr expr) {
    var resolvedExpr = expr.resolve(context);
    // in case we have un-messaged TyckException
    try (var delayedReporter = new DelayedReporter(reporter)) {
      var tycker = new ExprTycker(primFactory, delayedReporter, null);
      var desugar = desugarExpr(resolvedExpr, delayedReporter);
      return tycker.zonk(tycker.synthesize(desugar));
    }
  }

  private @NotNull Expr desugarExpr(@NotNull Expr expr, @NotNull Reporter reporter) {
    var resolveInfo = new ResolveInfo(primFactory,
      new EmptyContext(reporter, Path.of("dummy")).derive("dummy"),
      ImmutableSeq.empty(), new AyaBinOpSet(reporter));
    return new Desugarer.ForExpr(expr.view(), resolveInfo).commit();
  }

  /** @see ReplCompiler#compileExpr(String, NormalizeMode) */
  public int loadToContext(@NotNull Path file) throws IOException {
    if (Files.isDirectory(file)) return loadLibrary(file);
    return loadFile(file);
  }

  private int loadLibrary(@NotNull Path libraryRoot) throws IOException {
    var compiler = LibraryCompiler.newCompiler(primFactory, reporter, flags, libraryRoot);
    int result = compiler.start();
    var owner = compiler.libraryOwner();
    importModule(owner);
    return result;
  }

  private void importModule(@NotNull LibraryOwner owner) {
    owner.librarySources()
      .map(src -> src.resolveInfo().value.thisModule())
      .filterIsInstance(PhysicalModuleContext.class)
      .forEach(mod -> mod.exports.forEach((name, contents) -> context.importModule(
        Stmt.Accessibility.Public,
        SourcePos.NONE,
        name,
        contents
      )));
    owner.libraryDeps().forEach(this::importModule);
  }

  private int loadFile(@NotNull Path file) throws IOException {
    return new SingleFileCompiler(reporter, null, null)
      .compile(file, r -> context, flags, null);
  }

  /**
   * Copied and adapted.
   *
   * @param text the text of code to compile, witch might either be a `program` or an `expr`.
   * @see org.aya.cli.single.SingleFileCompiler#compile
   */
  public @NotNull Either<ImmutableSeq<Def>, Term> compileToContext(@NotNull String text, @NotNull NormalizeMode normalizeMode) {
    if (text.isBlank()) return Either.left(ImmutableSeq.empty());
    var locator = this.locator != null ? this.locator : new SourceFileLocator.Module(modulePaths);
    try {
      var programOrExpr = AyaParserImpl.repl(reporter, text);
      var loader = new CachedModuleLoader<>(new ModuleListLoader(reporter, modulePaths.view().map(path ->
        new FileModuleLoader(locator, path, reporter, new AyaParserImpl(reporter), primFactory, null)).toImmutableSeq()));
      return programOrExpr.map(
        program -> {
          var newDefs = new Ref<ImmutableSeq<Def>>();
          loader.tyckModule(primFactory, context.fork(), program, null, ((moduleResolve, defs) -> newDefs.set(defs)));
          if (reporter.anyError()) return ImmutableSeq.empty();
          context.merge();
          return newDefs.get();
        },
        expr -> tyckExpr(expr).wellTyped().normalize(new TyckState(primFactory), normalizeMode)
      );
    } catch (InterruptException ignored) {
      // Only two kinds of interruptions are possible: parsing and resolving
      return Either.left(ImmutableSeq.empty());
    }
  }

  /**
   * Adapted.
   *
   * @see #loadToContext
   */
  public @Nullable Term compileExpr(@NotNull String text, @NotNull NormalizeMode normalizeMode) {
    try {
      return tyckExpr(AyaParserImpl.replExpr(reporter, text)).type().normalize(new TyckState(primFactory), normalizeMode);
    } catch (InterruptException ignored) {
      return null;
    }
  }

  public @NotNull ReplContext getContext() {
    return context;
  }
}
