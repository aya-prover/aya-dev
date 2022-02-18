// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.order;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import kala.collection.mutable.MutableSet;
import kala.control.Option;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Sample;
import org.aya.core.def.Def;
import org.aya.core.def.FnDef;
import org.aya.core.term.Term;
import org.aya.generic.util.InterruptException;
import org.aya.resolve.ResolveInfo;
import org.aya.terck.CallGraph;
import org.aya.terck.CallResolver;
import org.aya.terck.error.NonTerminating;
import org.aya.tyck.StmtTycker;
import org.aya.tyck.error.CircularSignatureError;
import org.aya.tyck.trace.Trace;
import org.aya.util.reporter.CountingReporter;
import org.aya.util.reporter.Reporter;
import org.aya.util.tyck.SCCTycker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tyck statements in SCC.
 *
 * @author kiva
 */
public record AyaSccTycker(
  @NotNull StmtTycker tycker,
  @NotNull CountingReporter reporter,
  @NotNull ResolveInfo resolveInfo,
  @NotNull DynamicSeq<@NotNull Def> wellTyped
) implements SCCTycker<TyckOrder, AyaSccTycker.SCCTyckingFailed> {
  public static @NotNull AyaSccTycker create(ResolveInfo resolveInfo, @Nullable Trace.Builder builder, @NotNull Reporter outReporter) {
    var counting = CountingReporter.delegate(outReporter);
    return new AyaSccTycker(new StmtTycker(counting, builder), counting, resolveInfo, DynamicSeq.create());
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
    terck(scc.view().map(TyckOrder::unit));
  }

  private void checkUnit(@NotNull TyckOrder order) {
    if (order.unit() instanceof Decl.FnDecl fn && fn.body.isLeft()) checkSimpleFn(order, fn);
    else {
      check(order);
      if (order instanceof TyckOrder.Body body)
        terck(SeqView.of(body.unit()));
    }
  }

  private boolean isRecursive(@NotNull Decl unit) {
    return resolveInfo.depGraph().hasSuc(new TyckOrder.Body(unit), new TyckOrder.Body(unit));
  }

  private void checkSimpleFn(@NotNull TyckOrder order, @NotNull Decl.FnDecl fn) {
    if (isRecursive(fn)) {
      reporter.report(new NonTerminating(fn.sourcePos, fn.ref, null));
      throw new SCCTyckingFailed(ImmutableSeq.of(order));
    }
    wellTyped.append(tycker.simpleFn(tycker.newTycker(), fn));
  }

  private void check(@NotNull TyckOrder tyckOrder) {
    switch (tyckOrder) {
      case TyckOrder.Head head -> checkHeader(tyckOrder, head.unit());
      case TyckOrder.Body body -> checkBody(tyckOrder, body.unit());
    }
  }

  private void checkHeader(@NotNull TyckOrder order, @NotNull TyckUnit stmt) {
    switch (stmt) {
      case Decl decl -> tycker.tyckHeader(decl, tycker.newTycker());
      case Sample sample -> sample.tyckHeader(tycker);
      case Decl.DataCtor ctor -> tycker.visitCtor(ctor, tycker.newTycker());
      case Decl.StructField field -> tycker.visitField(field, tycker.newTycker());
      default -> {}
    }
    if (reporter.anyError()) throw new SCCTyckingFailed(ImmutableSeq.of(order));
  }

  private void checkBody(@NotNull TyckOrder order, @NotNull TyckUnit stmt) {
    switch (stmt) {
      case Decl decl -> {
        var tyck = tycker.tyck(decl, tycker.newTycker());
        wellTyped.append(tyck);
      }
      case Sample sample -> {
        var tyck = sample.tyck(tycker);
        if (tyck != null) wellTyped.append(tyck);
      }
      case Remark remark -> Option.of(remark.literate).forEach(l -> l.tyck(tycker.newTycker()));
      default -> {}
    }
    if (reporter.anyError()) throw new SCCTyckingFailed(ImmutableSeq.of(order));
  }

  private void terck(@NotNull SeqView<TyckUnit> units) {
    // TODO: terck other definitions
    var fn = units.filterIsInstance(Decl.FnDecl.class)
      .map(decl -> decl.ref.core)
      .filter(def -> isRecursive(def.ref().concrete));
    if (fn.isEmpty()) return;
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
