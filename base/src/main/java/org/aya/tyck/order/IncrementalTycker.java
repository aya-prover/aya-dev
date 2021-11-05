// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.order;

import kala.collection.SeqLike;
import kala.collection.mutable.MutableSet;
import org.aya.concrete.resolve.ResolveInfo;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Stmt;
import org.jetbrains.annotations.NotNull;

/**
 * Incremental and non-stopping compiler for SCCs.
 *
 * @author kiva
 */
public record IncrementalTycker(
  @NotNull SCCTycker sccTycker,
  @NotNull ResolveInfo resolveInfo,
  @NotNull MutableSet<Stmt> skipped
) {
  public IncrementalTycker(@NotNull SCCTycker sccTycker, @NotNull ResolveInfo resolveInfo) {
    this(sccTycker, resolveInfo, MutableSet.of());
  }

  public void tyckSCC(@NotNull SeqLike<Stmt> scc) {
    try {
      // we are more likely to check correct programs.
      // I'm not sure whether it's necessary to optimize on our own.
      if (skipped.isEmpty()) sccTycker.tyckSCC(scc);
      else sccTycker.tyckSCC(scc.view().filterNot(skipped::contains));
    } catch (SCCTycker.SCCTyckingFailed failed) {
      failed.what.forEach(this::skip);
    }
  }

  private void skip(@NotNull Stmt failed) {
    if (skipped.contains(failed)) return;
    skipped.add(failed);
    var graph = failed instanceof Decl ? resolveInfo.declGraph() : resolveInfo.sampleGraph();
    graph.E().forEach((v, deps) -> {
      if (deps.contains(failed)) skip(v);
    });
  }
}
