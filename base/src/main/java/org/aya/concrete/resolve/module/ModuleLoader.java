// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.resolve.module;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.error.DelayedReporter;
import org.aya.api.error.Reporter;
import org.aya.concrete.desugar.AyaBinOpSet;
import org.aya.concrete.resolve.ModuleCallback;
import org.aya.concrete.resolve.ResolveInfo;
import org.aya.concrete.resolve.context.ModuleContext;
import org.aya.concrete.stmt.Stmt;
import org.aya.tyck.order.AyaNonStoppingTicker;
import org.aya.tyck.order.AyaSccTycker;
import org.aya.tyck.trace.Trace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author re-xyr
 */
public interface ModuleLoader {
  default <E extends Exception> @NotNull ResolveInfo tyckModule(
    @NotNull ModuleContext context,
    @NotNull ImmutableSeq<Stmt> program,
    @Nullable Trace.Builder builder,
    @Nullable ModuleCallback<E> onTycked
  ) throws E {
    return tyckModule(builder, resolveModule(context, program), onTycked);
  }

  private <E extends Exception> @NotNull ResolveInfo
  tyckModule(Trace.Builder builder, ResolveInfo resolveInfo, ModuleCallback<E> onTycked) throws E {
    var delayedReporter = new DelayedReporter(reporter());
    var sccTycker = new AyaNonStoppingTicker(new AyaSccTycker(builder, delayedReporter), resolveInfo);
    // in case we have un-messaged TyckException
    try (delayedReporter) {
      var SCCs = resolveInfo.declGraph().topologicalOrder()
        .view().appendedAll(resolveInfo.sampleGraph().topologicalOrder())
        .toImmutableSeq();
      SCCs.forEach(sccTycker::tyckSCC);
    } finally {
      if (onTycked != null) onTycked.onModuleTycked(
        resolveInfo, sccTycker.sccTycker().wellTyped().toImmutableSeq());
    }
    return resolveInfo;
  }

  default @NotNull ResolveInfo resolveModule(
    @NotNull ModuleContext context,
    @NotNull ImmutableSeq<Stmt> program
  ) {
    var resolveInfo = new ResolveInfo(context, program, new AyaBinOpSet(reporter()));
    Stmt.resolve(program, resolveInfo, this);
    return resolveInfo;
  }

  @NotNull Reporter reporter();
  @Nullable ResolveInfo load(@NotNull ImmutableSeq<@NotNull String> path);
}
