// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.resolve;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import org.aya.concrete.desugar.AyaBinOpSet;
import org.aya.concrete.resolve.context.ModuleContext;
import org.aya.concrete.stmt.Stmt;
import org.aya.util.MutableGraph;
import org.jetbrains.annotations.NotNull;

/**
 * @param opSet       binary operators
 * @param declGraph   dependency graph of decls. Successors should be tycked first.
 * @param sampleGraph dependency graph of samples and remarks.
 */
public record ResolveInfo(
  @NotNull ModuleContext thisModule,
  @NotNull AyaBinOpSet opSet,
  @NotNull DynamicSeq<ImmutableSeq<String>> imports,
  @NotNull MutableGraph<Stmt> declGraph,
  @NotNull MutableGraph<Stmt> sampleGraph
) {
  public ResolveInfo(@NotNull ModuleContext thisModule, @NotNull AyaBinOpSet opSet) {
    this(thisModule, opSet, DynamicSeq.create(), MutableGraph.create(), MutableGraph.create());
  }

  public @NotNull ResolveInfo toUsageInfo() {
    return new ResolveInfo(thisModule, opSet, imports, declGraph.transpose(), sampleGraph.transpose());
  }
}
