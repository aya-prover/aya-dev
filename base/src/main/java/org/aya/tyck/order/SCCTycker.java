// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.order;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import kala.control.Option;
import org.aya.api.error.CollectingReporter;
import org.aya.api.error.Problem;
import org.aya.api.util.InterruptException;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Sample;
import org.aya.concrete.stmt.Stmt;
import org.aya.core.def.Def;
import org.aya.tyck.StmtTycker;
import org.aya.tyck.error.CircularSignatureError;
import org.aya.tyck.trace.Trace;
import org.aya.util.MutableGraph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Tyck statements in SCC.
 *
 * @author kiva
 */
public record SCCTycker(
  @NotNull StmtTycker tycker,
  @NotNull CollectingReporter reporter,
  @NotNull DynamicSeq<@NotNull Def> wellTyped
) {
  public SCCTycker(@Nullable Trace.Builder builder, @NotNull CollectingReporter reporter) {
    this(new StmtTycker(reporter, builder), reporter, DynamicSeq.create());
  }

  public void tyckSCC(@NotNull ImmutableSeq<Stmt> scc) throws SCCTyckingFailed {
    if (scc.sizeEquals(1)) checkBody(scc.first());
    else {
      var headerOrder = headerOrder(scc);
      headerOrder.forEach(this::checkHeader);
      headerOrder.forEach(this::checkBody);
    }
  }

  private void checkHeader(@NotNull Stmt stmt) {
    if (stmt instanceof Decl decl) tycker.tyckHeader(decl, tycker.newTycker());
    else if (stmt instanceof Sample sample) sample.tyckHeader(tycker);
    if (reporter.problems().anyMatch(Problem::isError)) throw new SCCTyckingFailed(ImmutableSeq.of(stmt));
  }

  private void checkBody(@NotNull Stmt stmt) {
    switch (stmt) {
      case Decl decl -> wellTyped.append(tycker.tyck(decl, tycker.newTycker()));
      case Sample sample -> {
        var tyck = sample.tyck(tycker);
        if (tyck != null) wellTyped.append(tyck);
      }
      case Remark remark -> Option.of(remark.literate).forEach(l -> l.tyck(tycker.newTycker()));
      default -> {}
    }
    if (reporter.problems().anyMatch(Problem::isError)) throw new SCCTyckingFailed(ImmutableSeq.of(stmt));
  }

  /**
   * Generate the order of dependency of headers, fail if a cycle occurs.
   *
   * @author re-xyr, kiva
   */
  public @NotNull ImmutableSeq<Stmt> headerOrder(@NotNull ImmutableSeq<Stmt> stmts) {
    var graph = MutableGraph.<Stmt>create();
    stmts.forEach(stmt -> {
      var reference = DynamicSeq.<Stmt>create();
      SigRefFinder.HEADER_ONLY.visit(stmt, reference);
      graph.suc(stmt).appendAll(reference);
    });
    var order = graph.topologicalOrder();
    var cycle = order.view().filter(s -> s.sizeGreaterThan(1));
    if (cycle.isNotEmpty()) {
      cycle.forEach(c -> reporter.report(new CircularSignatureError(c)));
      throw new SCCTyckingFailed(stmts);
    }
    return order.flatMap(Function.identity());
  }

  public static class SCCTyckingFailed extends InterruptException {
    public @NotNull ImmutableSeq<Stmt> what;

    public SCCTyckingFailed(@NotNull ImmutableSeq<Stmt> what) {
      this.what = what;
    }

    @Override public InterruptStage stage() {
      return InterruptStage.Tycking;
    }
  }
}
