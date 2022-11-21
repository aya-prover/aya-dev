// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.module;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.desugar.AyaBinOpSet;
import org.aya.concrete.stmt.Stmt;
import org.aya.core.def.PrimDef;
import org.aya.core.repr.AyaShape;
import org.aya.resolve.ModuleCallback;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.ModuleContext;
import org.aya.tyck.order.AyaOrgaTycker;
import org.aya.tyck.order.AyaSccTycker;
import org.aya.tyck.trace.Trace;
import org.aya.util.reporter.DelayedReporter;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author re-xyr
 */
public interface ModuleLoader {
  default <E extends Exception> @NotNull ResolveInfo tyckModule(
    @NotNull PrimDef.Factory primFactory,
    @NotNull AyaShape.Factory shapeFactory,
    @NotNull AyaBinOpSet opSet,
    @NotNull ModuleContext context,
    @NotNull ImmutableSeq<Stmt> program,
    @Nullable Trace.Builder builder,
    @Nullable ModuleCallback<E> onTycked
  ) throws E {
    return tyckModule(builder, resolveModule(primFactory, shapeFactory, opSet, context, program, this), onTycked);
  }

  default <E extends Exception> @NotNull ResolveInfo
  tyckModule(Trace.Builder builder, ResolveInfo resolveInfo, ModuleCallback<E> onTycked) throws E {
    var SCCs = resolveInfo.depGraph().topologicalOrder();
    var delayedReporter = new DelayedReporter(reporter());
    var sccTycker = new AyaOrgaTycker(AyaSccTycker.create(resolveInfo, builder, delayedReporter), resolveInfo);
    // in case we have un-messaged TyckException
    try (delayedReporter) {
      SCCs.forEach(sccTycker::tyckSCC);
    } finally {
      if (onTycked != null) onTycked.onModuleTycked(
        resolveInfo, sccTycker.sccTycker().wellTyped().toImmutableSeq());
    }
    return resolveInfo;
  }

  default @NotNull ResolveInfo resolveModule(
    @NotNull PrimDef.Factory primFactory,
    @NotNull AyaShape.Factory shapeFactory,
    @NotNull AyaBinOpSet opSet,
    @NotNull ModuleContext context,
    @NotNull ImmutableSeq<Stmt> program,
    @NotNull ModuleLoader recurseLoader
  ) {
    var resolveInfo = new ResolveInfo(primFactory, shapeFactory, opSet, context, program);
    Stmt.resolve(program, resolveInfo, recurseLoader);
    return resolveInfo;
  }

  @NotNull Reporter reporter();
  @Nullable ResolveInfo load(@NotNull ImmutableSeq<@NotNull String> path, @NotNull ModuleLoader recurseLoader);
  default @Nullable ResolveInfo load(@NotNull ImmutableSeq<@NotNull String> path) {
    return load(path, this);
  }
}
