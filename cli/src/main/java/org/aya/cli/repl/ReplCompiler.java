// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import kala.value.Ref;
import org.aya.api.error.CountingReporter;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourceFileLocator;
import org.aya.api.util.InterruptException;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.parse.AyaParsing;
import org.aya.concrete.resolve.context.EmptyContext;
import org.aya.concrete.resolve.module.CachedModuleLoader;
import org.aya.concrete.resolve.module.FileModuleLoader;
import org.aya.concrete.resolve.module.ModuleListLoader;
import org.aya.core.def.Def;
import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public class ReplCompiler {
  private final @NotNull Reporter reporter;
  private final @Nullable SourceFileLocator locator;
  private final @NotNull ReplContext context;

  ReplCompiler(@NotNull Reporter reporter, @Nullable SourceFileLocator locator) {
    this.reporter = reporter;
    this.locator = locator;
    this.context = new ReplContext(new EmptyContext(reporter), ImmutableSeq.of("REPL"));
  }

  /**
   * Copied and adapted.
   *
   * @param text the text of code to compile, witch might either be a `program` or an `expr`.
   * @see org.aya.cli.single.SingleFileCompiler#compile
   */
  @Nullable Either<ImmutableSeq<Def>, Term> compileAndAddToContext(
    @NotNull String text, @NotNull NormalizeMode normalizeMode,
    @NotNull SeqLike<Path> modulePaths,
    @Nullable FileModuleLoader.FileModuleLoaderCallback moduleCallback
  ) {
    if (text.isBlank()) return Either.left(ImmutableSeq.empty());
    var reporter = new CountingReporter(this.reporter);
    var locator = this.locator != null ? this.locator : new SourceFileLocator.Module(modulePaths);

    try {
      var programOrExpr = AyaParsing.repl(reporter, text);
      var loader = new ModuleListLoader(modulePaths.view().map(path ->
        new CachedModuleLoader(new FileModuleLoader(locator, path, reporter, moduleCallback, null))).toImmutableSeq());
      return programOrExpr.map(
        program -> {
          var newDefs = new Ref<ImmutableSeq<Def>>();
          FileModuleLoader.tyckModule(context, loader, program, reporter,
            resolveInfo -> {}, newDefs::set, null);
          return newDefs.get();
        },
        expr -> FileModuleLoader.tyckExpr(context, expr, reporter, null).wellTyped()
          .normalize(null, normalizeMode)
      );
    } catch (InterruptException ignored) {
      return Either.left(ImmutableSeq.empty());
    }
  }

  /**
   * Adapted.
   *
   * @see #compileAndAddToContext
   */
  public @Nullable Term compileExprAndGetType(
    @NotNull String text, @NotNull NormalizeMode normalizeMode
  ) {
    var reporter = new CountingReporter(this.reporter);
    try {
      var expr = AyaParsing.expr(reporter, text);
      return FileModuleLoader.tyckExpr(context, expr, reporter, null)
        .type().normalize(null, normalizeMode);
    } catch (InterruptException ignored) {
      return null;
    }
  }

  public @NotNull ReplContext getContext() {
    return context;
  }
}
