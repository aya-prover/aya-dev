// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.order;

import kala.collection.mutable.MutableSet;
import org.aya.concrete.resolve.ResolveInfo;
import org.aya.util.MutableGraph;
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
  @NotNull MutableGraph<TyckUnit> usageGraph,
  @NotNull MutableSet<TyckUnit> skippedSet
) implements OrgaTycker<TyckUnit, AyaSccTycker.SCCTyckingFailed> {
  public AyaOrgaTycker(@NotNull AyaSccTycker sccTycker, @NotNull ResolveInfo resolveInfo) {
    this(sccTycker, resolveInfo.depGraph().transpose(), MutableSet.of());
  }

  @Override public @NotNull Iterable<TyckUnit> collectUsageOf(@NotNull TyckUnit failed) {
    return usageGraph.suc(failed);
  }
}
