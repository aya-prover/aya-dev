// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.order;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableSet;
import org.aya.concrete.resolve.ResolveInfo;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Stmt;
import org.aya.util.MutableGraph;
import org.jetbrains.annotations.NotNull;

/**
 * Incremental and non-stopping compiler for SCCs.
 *
 * @param declUsage   transpose of {@link ResolveInfo#declGraph()}. Vertex should be tycked first.
 * @param sampleUsage transpose of {@link ResolveInfo#sampleGraph()}
 * @author kiva
 */
public record IncrementalTycker(
  @NotNull SCCTycker sccTycker,
  @NotNull MutableGraph<Stmt> declUsage,
  @NotNull MutableGraph<Stmt> sampleUsage,
  @NotNull MutableSet<Stmt> skipped
) {
  public IncrementalTycker(@NotNull SCCTycker sccTycker, @NotNull ResolveInfo resolveInfo) {
    this(sccTycker, resolveInfo.declGraph().transpose(), resolveInfo.sampleGraph().transpose(), MutableSet.of());
  }

  public void tyckSCC(@NotNull ImmutableSeq<Stmt> scc) {
    try {
      // we are more likely to check correct programs.
      // I'm not sure whether it's necessary to optimize on our own.
      if (skipped.isEmpty()) sccTycker.tyckSCC(scc);
      else sccTycker.tyckSCC(scc.filterNot(skipped::contains));
    } catch (SCCTycker.SCCTyckingFailed failed) {
      failed.what.forEach(this::skip);
    }
  }

  private void skip(@NotNull Stmt failed) {
    if (skipped.contains(failed)) return;
    skipped.add(failed);
    var graph = failed instanceof Decl ? declUsage : sampleUsage;
    graph.suc(failed).forEach(this::skip);
  }
}
