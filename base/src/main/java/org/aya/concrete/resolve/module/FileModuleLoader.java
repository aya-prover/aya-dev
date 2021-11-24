// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.resolve.module;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.error.DelayedReporter;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourceFileLocator;
import org.aya.api.util.InternalException;
import org.aya.concrete.Expr;
import org.aya.concrete.parse.AyaParsing;
import org.aya.concrete.resolve.ResolveInfo;
import org.aya.concrete.resolve.context.EmptyContext;
import org.aya.concrete.resolve.context.ModuleContext;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.trace.Trace;
import org.aya.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

public record FileModuleLoader(
  @NotNull SourceFileLocator locator,
  @NotNull Path basePath,
  @Override @NotNull Reporter reporter,
  Trace.@Nullable Builder builder
) implements ModuleLoader {
  @Override public @Nullable ResolveInfo load(@NotNull ImmutableSeq<@NotNull String> path) {
    var sourcePath = FileUtil.resolveFile(basePath, path, ".aya");
    try {
      var program = AyaParsing.program(locator, reporter, sourcePath);
      var context = new EmptyContext(reporter, sourcePath).derive(path);
      return tyckModule(context, program, builder, null);
    } catch (IOException e) {
      return null;
    }
  }

  /**
   * Copied and adapted.
   *
   * @see ModuleLoader#tyckModule
   */
  public static ExprTycker.@NotNull Result tyckExpr(
    @NotNull ModuleContext context,
    @NotNull Expr expr,
    @NotNull Reporter reporter,
    @Nullable Trace.Builder builder
  ) {
    var resolvedExpr = expr.resolve(context);
    // in case we have un-messaged TyckException
    try (var delayedReporter = new DelayedReporter(reporter)) {
      var tycker = new ExprTycker(delayedReporter, builder);
      return tycker.zonk(expr, tycker.synthesize(resolvedExpr.desugar(delayedReporter)));
    }
  }

  public static void handleInternalError(@NotNull InternalException e) {
    e.printStackTrace();
    e.printHint();
    System.err.println("""
      Please report the stacktrace to the developers so a better error handling could be made.
      Don't forget to inform the version of Aya you're using and attach your code for reproduction.""");
  }
}
