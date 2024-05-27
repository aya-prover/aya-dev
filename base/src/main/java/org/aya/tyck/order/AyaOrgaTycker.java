// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.order;

import kala.collection.mutable.MutableSet;
import org.aya.generic.stmt.TyckOrder;
import org.aya.resolve.ResolveInfo;
import org.aya.util.terck.MutableGraph;
import org.aya.util.tyck.OrgaTycker;
import org.jetbrains.annotations.NotNull;

/**
 * Incremental and non-stopping compiler for SCCs.
 *
 * @param usageGraph usage graph of decls (usually the transpose of {@link ResolveInfo#depGraph()}).
 *                   for each (vertex, w) in the graph, the vertex should be tycked first.
 * @author kiva
 */
public record AyaOrgaTycker(
  @NotNull AyaSccTycker sccTycker,
  @NotNull MutableGraph<TyckOrder> usageGraph,
  @NotNull MutableSet<TyckOrder> skippedSet
) implements OrgaTycker<TyckOrder, AyaSccTycker.SCCTyckingFailed> {
  public AyaOrgaTycker(@NotNull AyaSccTycker sccTycker, @NotNull ResolveInfo resolveInfo) {
    this(sccTycker, resolveInfo.depGraph().transpose(), MutableSet.create());
  }

  @Override public @NotNull Iterable<TyckOrder> collectUsageOf(@NotNull TyckOrder failed) {
    return usageGraph.suc(failed);
  }
}
