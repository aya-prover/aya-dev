// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.order;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.Buffer;
import kala.control.Option;
import kala.tuple.Tuple;
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
public record SCCTycker(Trace.@Nullable Builder builder, CollectingReporter reporter) {
  public @NotNull ImmutableSeq<Def> tyckSCC(@NotNull ImmutableSeq<Stmt> scc) {
    if (scc.sizeEquals(1))
      return checkBody(new StmtTycker(reporter, builder), scc.first(), Buffer.create()).toImmutableSeq();
    var headerOrder = headerOrder(scc).view()
      .map(s -> Tuple.of(s, new StmtTycker(reporter, builder)));
    headerOrder.forEach(tup -> checkHeader(tup._2, tup._1));
    var wellTyped = Buffer.<Def>create();
    headerOrder.forEach(tup -> checkBody(tup._2, tup._1, wellTyped));
    return wellTyped.toImmutableSeq();
  }

  private void checkHeader(@NotNull StmtTycker tycker, @NotNull Stmt stmt) {
    if (stmt instanceof Decl decl) tycker.tyckHeader(decl, tycker.newTycker());
    else if (stmt instanceof Sample sample) sample.tyckHeader(tycker);
  }

  private @NotNull Buffer<Def> checkBody(@NotNull StmtTycker tycker, @NotNull Stmt stmt, @NotNull Buffer<Def> wellTyped) {
    switch (stmt) {
      case Decl decl -> wellTyped.append(tycker.tyck(decl, tycker.newTycker()));
      case Sample sample -> wellTyped.append(sample.tyck(tycker));
      case Remark remark -> Option.of(remark.literate).forEach(l -> l.tyck(tycker.newTycker()));
      default -> {}
    }
    if (reporter.problems().anyMatch(Problem::isError)) throw new SCCTyckingFailed();
    return wellTyped;
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
