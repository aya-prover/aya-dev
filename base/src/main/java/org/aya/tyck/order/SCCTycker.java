// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.order;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.Buffer;
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
public record SCCTycker(@NotNull StmtTycker tycker, @NotNull CollectingReporter reporter) {
  public SCCTycker(@Nullable Trace.Builder builder, @NotNull CollectingReporter reporter) {
    this(new StmtTycker(reporter, builder), reporter);
  }

  public @NotNull ImmutableSeq<Def> tyckSCC(@NotNull ImmutableSeq<Stmt> scc) {
    var wellTyped = Buffer.<Def>create();
    if (scc.sizeEquals(1)) checkBody(scc.first(), wellTyped);
    else {
      var headerOrder = headerOrder(scc);
      headerOrder.forEach(this::checkHeader);
      headerOrder.forEach(tup -> checkBody(tup, wellTyped));
    }
    return wellTyped.toImmutableSeq();
  }

  private void checkHeader(@NotNull Stmt stmt) {
    if (stmt instanceof Decl decl) tycker.tyckHeader(decl, tycker.newTycker());
    else if (stmt instanceof Sample sample) sample.tyckHeader(tycker);
  }

  private void checkBody(@NotNull Stmt stmt, @NotNull Buffer<Def> wellTyped) {
    switch (stmt) {
      case Decl decl -> wellTyped.append(tycker.tyck(decl, tycker.newTycker()));
      case Sample sample -> wellTyped.append(sample.tyck(tycker));
      case Remark remark -> Option.of(remark.literate).forEach(l -> l.tyck(tycker.newTycker()));
      default -> {}
    }
    if (reporter.problems().anyMatch(Problem::isError)) throw new SCCTyckingFailed();
  }

  /**
   * Generate the order of dependency of headers, fail if a cycle occurs.
   *
   * @author re-xyr, kiva
   */
  public @NotNull ImmutableSeq<Stmt> headerOrder(@NotNull Seq<Stmt> stmts) {
    var graph = MutableGraph.<Stmt>empty();
    stmts.forEach(stmt -> {
      var reference = Buffer.<Stmt>create();
      stmt.accept(SigRefFinder.HEADER_ONLY, reference);
      graph.suc(stmt).addAll(reference);
    });
    var order = graph.topologicalOrder();
    var cycle = order.view().filter(s -> s.sizeGreaterThan(1));
    if (cycle.isNotEmpty()) {
      cycle.forEach(c -> reporter.report(new CircularSignatureError(c)));
      throw new SCCTyckingFailed();
    }
    return order.flatMap(Function.identity());
  }

  public static class SCCTyckingFailed extends InterruptException {
    @Override public InterruptStage stage() {
      return InterruptStage.Tycking;
    }
  }
}
