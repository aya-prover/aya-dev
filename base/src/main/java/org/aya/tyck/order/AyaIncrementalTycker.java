// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.order;

import kala.collection.mutable.MutableSet;
import org.aya.concrete.resolve.ResolveInfo;
import org.aya.concrete.stmt.Decl;
import org.aya.util.MutableGraph;
import org.aya.util.tyck.IncrementalTycker;
import org.jetbrains.annotations.NotNull;

/**
 * Incremental and non-stopping compiler for SCCs.
 *
 * @param declUsage   usage graph of decls (usually the transpose of {@link ResolveInfo#declGraph()}).
 *                    for each (vertex, w) in the graph, the vertex should be tycked first.
 * @param sampleUsage transpose of {@link ResolveInfo#sampleGraph()}
 * @author kiva
 */
public record AyaIncrementalTycker(
  @NotNull AyaSccTycker sccTycker,
  @NotNull MutableGraph<TyckUnit> declUsage,
  @NotNull MutableGraph<TyckUnit> sampleUsage,
  @NotNull MutableSet<TyckUnit> skippedSet
) implements IncrementalTycker<TyckUnit> {
  public AyaIncrementalTycker(@NotNull AyaSccTycker sccTycker, @NotNull ResolveInfo resolveInfo) {
    this(sccTycker, resolveInfo.declGraph().transpose(), resolveInfo.sampleGraph().transpose(), MutableSet.of());
  }

  @Override public @NotNull Iterable<TyckUnit> collectUsageOf(@NotNull TyckUnit failed) {
    var graph = failed instanceof Decl ? declUsage : sampleUsage;
    return graph.suc(failed);
  }
}
