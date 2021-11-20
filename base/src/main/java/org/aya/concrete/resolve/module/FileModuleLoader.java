// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.resolve.module;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import org.aya.api.error.DelayedReporter;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourceFileLocator;
import org.aya.api.util.InternalException;
import org.aya.concrete.Expr;
import org.aya.concrete.desugar.AyaBinOpSet;
import org.aya.concrete.parse.AyaParsing;
import org.aya.concrete.resolve.ModuleCallback;
import org.aya.concrete.resolve.ResolveInfo;
import org.aya.concrete.resolve.context.EmptyContext;
import org.aya.concrete.resolve.context.ModuleContext;
import org.aya.concrete.stmt.Stmt;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.order.IncrementalTycker;
import org.aya.tyck.order.SCCTycker;
import org.aya.tyck.trace.Trace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

public record FileModuleLoader(
  @NotNull SourceFileLocator locator,
  @NotNull Path basePath,
  @NotNull Reporter reporter,
  Trace.@Nullable Builder builder
) implements ModuleLoader {
  private @NotNull Path resolveFile(@NotNull Seq<@NotNull String> path) {
    var withoutExt = path.foldLeft(basePath, Path::resolve);
    return withoutExt.resolveSibling(withoutExt.getFileName() + ".aya");
  }

  @Override
  public @Nullable ResolveInfo load(@NotNull ImmutableSeq<@NotNull String> path, @NotNull ModuleLoader recurseLoader) {
    var sourcePath = resolveFile(path);
    try {
      var program = AyaParsing.program(locator, reporter, sourcePath);
      var context = new EmptyContext(reporter, sourcePath).derive(path);
      return tyckModule(context, recurseLoader, program, reporter, null, builder);
    } catch (IOException e) {
      return null;
    }
  }

  public static <E extends Exception> @NotNull ResolveInfo tyckModule(
    @NotNull ModuleContext context,
    @NotNull ModuleLoader recurseLoader,
    @NotNull ImmutableSeq<Stmt> program,
    @NotNull Reporter reporter,
    @Nullable ModuleCallback<E> onTycked,
    @Nullable Trace.Builder builder
  ) throws E {
    var resolveInfo = new ResolveInfo(context, new AyaBinOpSet(reporter));
    Stmt.resolve(program, resolveInfo, recurseLoader);
    var delayedReporter = new DelayedReporter(reporter);
    var sccTycker = new IncrementalTycker(new SCCTycker(builder, delayedReporter), resolveInfo);
    // in case we have un-messaged TyckException
    try (delayedReporter) {
      var SCCs = resolveInfo.declGraph().topologicalOrder()
        .view().appendedAll(resolveInfo.sampleGraph().topologicalOrder())
        .toImmutableSeq();
      SCCs.forEach(sccTycker::tyckSCC);
    } finally {
      if (onTycked != null) onTycked.onModuleTycked(resolveInfo, program,
        sccTycker.sccTycker().wellTyped().toImmutableSeq());
    }
    return resolveInfo;
  }

  /**
   * Copied and adapted.
   *
   * @see #tyckModule
   */
  public static ExprTycker.@NotNull Result tyckExpr(
    @NotNull ModuleContext context,
    @NotNull Expr expr,
    @NotNull Reporter reporter,
    Trace.@Nullable Builder builder
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
