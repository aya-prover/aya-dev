// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.order;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.Buffer;
import kala.control.Option;
import org.aya.api.error.Problem;
import org.aya.api.error.StoringReporter;
import org.aya.api.util.InterruptException;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Sample;
import org.aya.concrete.stmt.Stmt;
import org.aya.core.def.Def;
import org.aya.tyck.ExprTycker;
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
public record SCCTycker(Trace.@Nullable Builder builder, StoringReporter reporter) {
  public @NotNull ImmutableSeq<Def> tyckSCC(@NotNull ImmutableSeq<Stmt> scc) {
    if (scc.sizeEquals(1)) return checkOne(reporter, scc.first(), false, Buffer.create()).toImmutableSeq();
    var headerOrder = headerOrder(scc);
    headerOrder.forEach(stmt -> checkOne(reporter, stmt, true, null));
    var wellTyped = Buffer.<Def>create();
    headerOrder.forEach(stmt -> checkOne(reporter, stmt, false, wellTyped));
    return wellTyped.toImmutableSeq();
  }

  private @Nullable Buffer<Def> checkOne(@NotNull StoringReporter reporter,
                                         @NotNull Stmt stmt, boolean headerOnly,
                                         @Nullable Buffer<Def> wellTyped) {
    try {
      var def = switch (stmt) {
        case Decl decl -> Option.some(decl.tyck(reporter, builder, headerOnly));
        case Sample sample -> Option.some(sample.tyck(reporter, builder, headerOnly));
        case Remark remark && !headerOnly -> {
          var literate = remark.literate;
          if (literate != null) literate.tyck(new ExprTycker(reporter, builder));
          yield Option.<Def>none();
        }
        default -> Option.<Def>none();
      };
      if (wellTyped != null && def.isDefined()) wellTyped.append(def.get());
    } catch (StmtTycker.HeaderOnlyException ignored) {
      assert headerOnly : "HeaderOnlyException was wrongly thrown";
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
