// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.order;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableSet;
import kala.control.Option;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.stmt.Decl;
import org.aya.core.def.Def;
import org.aya.core.def.FnDef;
import org.aya.core.def.UserDef;
import org.aya.core.term.Term;
import org.aya.generic.util.InterruptException;
import org.aya.resolve.ResolveInfo;
import org.aya.terck.CallGraph;
import org.aya.terck.CallResolver;
import org.aya.terck.error.NonTerminating;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.StmtTycker;
import org.aya.tyck.error.CircularSignatureError;
import org.aya.tyck.error.CounterexampleError;
import org.aya.tyck.trace.Trace;
import org.aya.util.MutableGraph;
import org.aya.util.reporter.BufferReporter;
import org.aya.util.reporter.CollectingReporter;
import org.aya.util.reporter.CountingReporter;
import org.aya.util.reporter.Reporter;
import org.aya.util.tyck.SCCTycker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tyck statements in SCC.
 *
 * @param tyckerReuse headers and bodies should be checked using the same tycker.
 * @author kiva
 * @see ExprTycker
 */
public record AyaSccTycker(
  @NotNull StmtTycker tycker,
  @NotNull CountingReporter reporter,
  @NotNull ResolveInfo resolveInfo,
  @NotNull DynamicSeq<@NotNull Def> wellTyped,
  @NotNull MutableMap<Decl, ExprTycker> tyckerReuse,
  @NotNull MutableMap<Decl, CollectingReporter> sampleReporters
) implements SCCTycker<TyckOrder, AyaSccTycker.SCCTyckingFailed> {
  public static @NotNull AyaSccTycker create(ResolveInfo resolveInfo, @Nullable Trace.Builder builder, @NotNull Reporter outReporter) {
    var counting = CountingReporter.delegate(outReporter);
    return new AyaSccTycker(new StmtTycker(counting, builder), counting, resolveInfo, DynamicSeq.create(), MutableMap.create(), MutableMap.create());
  }

  public @NotNull ImmutableSeq<TyckOrder> tyckSCC(@NotNull ImmutableSeq<TyckOrder> scc) {
    try {
      if (scc.isEmpty()) return ImmutableSeq.empty();
      if (scc.sizeEquals(1)) checkUnit(scc.first());
      else checkMutual(scc);
      return ImmutableSeq.empty();
    } catch (SCCTyckingFailed failed) {
      reporter.clear();
      return failed.what;
    }
  }

  private void checkMutual(@NotNull ImmutableSeq<TyckOrder> scc) {
    if (scc.allMatch(t -> t instanceof TyckOrder.Head)) {
      reporter.report(new CircularSignatureError(scc.map(TyckOrder::unit)));
      throw new SCCTyckingFailed(scc);
    }
    scc.forEach(this::check);
    terck(scc.view());
  }

  private void checkUnit(@NotNull TyckOrder order) {
    if (order instanceof TyckOrder.Body && order.unit() instanceof Decl.FnDecl fn && fn.body.isLeft()) {
      checkSimpleFn(order, fn);
    } else {
      check(order);
      if (order instanceof TyckOrder.Body body)
        terck(SeqView.of(body));
    }
  }

  private boolean hasSuc(
    @NotNull MutableGraph<TyckOrder> G,
    @NotNull MutableSet<TyckOrder> book,
    @NotNull TyckOrder vertex,
    @NotNull TyckOrder suc
  ) {
    if (book.contains(vertex)) return false;
    book.add(vertex);
    for (var test : G.suc(vertex)) {
      if (test.equals(suc)) return true;
      if (hasSuc(G, book, test, suc)) return true;
    }
    return false;
  }

  private boolean isRecursive(@NotNull TyckOrder unit) {
    return hasSuc(resolveInfo.depGraph(), MutableSet.create(), unit, unit);
  }

  private void checkSimpleFn(@NotNull TyckOrder order, @NotNull Decl.FnDecl fn) {
    if (isRecursive(order)) {
      reporter.report(new NonTerminating(fn.sourcePos, fn.ref, null));
      throw new SCCTyckingFailed(ImmutableSeq.of(order));
    }
    decideTyckResult(fn, tycker.simpleFn(tycker.newTycker(), fn));
  }

  private void check(@NotNull TyckOrder tyckOrder) {
    switch (tyckOrder) {
      case TyckOrder.Head head -> checkHeader(tyckOrder, head.unit());
      case TyckOrder.Body body -> checkBody(tyckOrder, body.unit());
    }
  }

  private void checkHeader(@NotNull TyckOrder order, @NotNull TyckUnit stmt) {
    switch (stmt) {
      case Decl decl -> tycker.tyckHeader(decl, reuse(decl));
      case Decl.DataCtor ctor -> tycker.tyckHeader(ctor, reuse(ctor.dataRef.concrete));
      case Decl.StructField field -> tycker.tyckHeader(field, reuse(field.structRef.concrete));
      default -> {}
    }
    if (reporter.anyError()) throw new SCCTyckingFailed(ImmutableSeq.of(order));
  }

  private void checkBody(@NotNull TyckOrder order, @NotNull TyckUnit stmt) {
    switch (stmt) {
      case Decl decl -> decideTyckResult(decl, tycker.tyck(decl, reuse(decl)));
      case Decl.DataCtor ctor -> tycker.tyck(ctor, reuse(ctor.dataRef.concrete));
      case Decl.StructField field -> tycker.tyck(field, reuse(field.structRef.concrete));
      case Remark remark -> Option.of(remark.literate).forEach(l -> l.tyck(tycker.newTycker()));
      default -> {}
    }
    if (reporter.anyError()) throw new SCCTyckingFailed(ImmutableSeq.of(order));
  }

  private void decideTyckResult(@NotNull Decl decl, @NotNull Def def) {
    if (decl.personality == Decl.Personality.NORMAL)
      wellTyped.append(def);
    else if (decl.personality == Decl.Personality.COUNTEREXAMPLE) {
      var sampleReporter = sampleReporters.getOrPut(decl, BufferReporter::new);
      var problems = sampleReporter.problems().toImmutableSeq();
      if (problems.isEmpty()) reporter.report(new CounterexampleError(decl.sourcePos, decl.ref()));
      if (def instanceof UserDef userDef) userDef.problems = problems;
    }
  }

  private @NotNull ExprTycker reuse(@NotNull Decl decl) {
    // prevent counterexample errors from being reported to the user reporter
    if (decl.personality == Decl.Personality.COUNTEREXAMPLE)
      return tyckerReuse.getOrPut(decl, () -> new ExprTycker(sampleReporters.getOrPut(decl, BufferReporter::new), tycker.traceBuilder()));
    return tyckerReuse.getOrPut(decl, tycker::newTycker);
  }

  private void terck(@NotNull SeqView<TyckOrder> units) {
    var recDefs = units.filterIsInstance(TyckOrder.Body.class)
      .filter(this::isRecursive)
      .map(TyckOrder::unit);
    if (recDefs.isEmpty()) return;
    // TODO: terck other definitions
    var fn = recDefs.filterIsInstance(Decl.FnDecl.class)
      .map(f -> f.ref.core);
    terckRecursiveFn(fn);
  }

  private void terckRecursiveFn(@NotNull SeqView<FnDef> fn) {
    var targets = MutableSet.<Def>from(fn);
    var graph = CallGraph.<Def, Term.Param>create();
    fn.forEach(def -> def.accept(new CallResolver(def, targets), graph));
    var failed = graph.findNonTerminating();
    if (failed != null) failed.forEach(f -> {
      var ref = f.matrix().domain().ref();
      reporter.report(new NonTerminating(ref.concrete.sourcePos, ref, f));
    });
  }

  public static class SCCTyckingFailed extends InterruptException {
    public final @NotNull ImmutableSeq<TyckOrder> what;

    public SCCTyckingFailed(@NotNull ImmutableSeq<TyckOrder> what) {
      this.what = what;
    }

    @Override public InterruptStage stage() {
      return InterruptStage.Tycking;
    }
  }
}
