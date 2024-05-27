// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.order;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableSet;
import org.aya.generic.InterruptException;
import org.aya.generic.stmt.TyckOrder;
import org.aya.generic.stmt.TyckUnit;
import org.aya.resolve.ResolveInfo;
import org.aya.syntax.concrete.stmt.decl.Decl;
import org.aya.syntax.concrete.stmt.decl.FnBody;
import org.aya.syntax.concrete.stmt.decl.FnDecl;
import org.aya.syntax.core.def.FnDef;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.core.term.call.Callable;
import org.aya.syntax.ref.DefVar;
import org.aya.terck.BadRecursion;
import org.aya.terck.CallResolver;
import org.aya.tyck.StmtTycker;
import org.aya.tyck.error.TyckOrderError;
import org.aya.tyck.tycker.Problematic;
import org.aya.util.error.Panic;
import org.aya.util.reporter.CountingReporter;
import org.aya.util.reporter.Reporter;
import org.aya.util.terck.CallGraph;
import org.aya.util.terck.Diagonal;
import org.aya.util.terck.MutableGraph;
import org.aya.util.tyck.SCCTycker;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * Tyck statements in SCC.
 *
 * @see org.aya.tyck.ExprTycker
 */
public record AyaSccTycker(
  @NotNull StmtTycker tycker,
  @NotNull CountingReporter reporter,
  @NotNull ResolveInfo resolveInfo,
  @NotNull MutableList<@NotNull TyckDef> wellTyped
) implements SCCTycker<TyckOrder, AyaSccTycker.SCCTyckingFailed>, Problematic {
  public static @NotNull AyaSccTycker create(ResolveInfo info, @NotNull Reporter outReporter) {
    var counting = CountingReporter.delegate(outReporter);
    var stmt = new StmtTycker(counting, info.shapeFactory(), info.primFactory());
    return new AyaSccTycker(stmt, counting, info, MutableList.create());
  }

  @Override public @NotNull ImmutableSeq<TyckOrder>
  tyckSCC(@NotNull ImmutableSeq<TyckOrder> scc) throws SCCTyckingFailed {
    try {
      if (scc.isEmpty()) return ImmutableSeq.empty();
      if (scc.sizeEquals(1)) checkUnit(scc.getFirst());
      else checkMutual(scc);
      return ImmutableSeq.empty();
    } catch (SCCTyckingFailed failed) {
      reporter.clear();
      return failed.what;
    }
  }

  private void checkMutual(@NotNull ImmutableSeq<TyckOrder> scc) {
    var heads = scc.filterIsInstance(TyckOrder.Head.class);
    if (heads.sizeGreaterThanOrEquals(2)) {
      fail(new TyckOrderError.CircularSignature(heads.map(TyckOrder.Head::unit)));
      throw new SCCTyckingFailed(scc);
    }
    throw new Panic("This place is in theory unreachable, we need to investigate if it is reached");
    /*
    var unit = scc.view().map(TyckOrder::unit)
      .distinct()
      .sorted(Comparator.comparing(SourceNode::sourcePos))
      .toImmutableSeq();
    if (unit.sizeEquals(1)) checkUnit(new TyckOrder.Body(unit.getFirst()));
    else {
      unit.forEach(u -> check(new TyckOrder.Head(u)));
      unit.forEach(u -> check(new TyckOrder.Body(u)));
      // terck(scc.view());
    }
    */
  }

  private void check(@NotNull TyckOrder tyckOrder) {
    switch (tyckOrder) {
      case TyckOrder.Head head -> checkHeader(tyckOrder, head.unit());
      case TyckOrder.Body body -> checkBody(tyckOrder, body.unit());
    }
  }

  private void checkUnit(@NotNull TyckOrder order) {
    if (order.unit() instanceof FnDecl fn && fn.body instanceof FnBody.ExprBody) {
      if (selfReferencing(resolveInfo.depGraph(), order)) {
        fail(new BadRecursion(fn.sourcePos(), fn.ref, null));
        throw new SCCTyckingFailed(ImmutableSeq.of(order));
      }
      check(new TyckOrder.Body(fn));
    } else {
      check(order);
      if (order instanceof TyckOrder.Body body) terck(ImmutableSeq.of(body));
    }
  }
  private void terck(@NotNull ImmutableSeq<TyckOrder.Body> units) {
    var recDefs = units.view()
      .filter(u -> selfReferencing(resolveInfo.depGraph(), u))
      .map(TyckOrder::unit)
      .toImmutableSeq();
    if (recDefs.isEmpty()) return;
    // TODO: positivity check for data/record definitions
    var fn = recDefs.view()
      .filterIsInstance(FnDecl.class)
      .map(f -> f.ref.core)
      .toImmutableSeq();
    terckRecursiveFn(fn);
  }

  private void terckRecursiveFn(@NotNull ImmutableSeq<FnDef> fn) {
    var targets = MutableSet.<TyckDef>from(fn);
    if (targets.isEmpty()) return;
    var graph = CallGraph.<Callable.Tele, TyckDef>create();
    fn.forEach(def -> new CallResolver(resolveInfo.makeTyckState(), def, targets, graph).check());
    graph.findBadRecursion().view()
      .sorted(Comparator.comparing(a -> domRef(a).concrete.sourcePos()))
      .forEach(f -> {
        var ref = domRef(f);
        fail(new BadRecursion(ref.concrete.sourcePos(), ref, f));
      });
  }

  private static @NotNull DefVar<?, ?> domRef(Diagonal<?, TyckDef> f) {
    return f.matrix().domain().ref();
  }

  private void checkHeader(@NotNull TyckOrder order, @NotNull TyckUnit stmt) {
    if (stmt instanceof Decl decl) tycker.checkHeader(decl);
    if (reporter.anyError()) throw new SCCTyckingFailed(ImmutableSeq.of(order));
  }

  private void checkBody(@NotNull TyckOrder order, @NotNull TyckUnit stmt) {
    if (stmt instanceof Decl decl) {
      var def = tycker.check(decl);
      if (!decl.isExample) {
        // In case I'm not an example, remember me and recognize my shape
        wellTyped.append(def);
        tycker.shapeFactory().bonjour(def);
      }
    }
    if (reporter.anyError()) throw new SCCTyckingFailed(ImmutableSeq.of(order));
  }

  /**
   * For self-reference check only, and this is nontrivial, as when it sees a dependency on a head,
   * it checks the upstream of the body too.
   *
   * @see #selfReferencing
   */
  private boolean hasSuc(
    @NotNull MutableGraph<TyckOrder> G,
    @NotNull MutableSet<TyckUnit> book,
    @NotNull TyckOrder vertex, @NotNull TyckOrder suc
  ) {
    if (book.contains(vertex.unit())) return false;
    book.add(vertex.unit());
    for (var test : G.suc(vertex)) {
      if (test.unit() == suc.unit()) return true;
      if (hasSuc(G, book, test, suc)) return true;
    }
    if (vertex instanceof TyckOrder.Head head)
      return hasSuc(G, book, head.toBody(), suc);
    return false;
  }

  private boolean selfReferencing(@NotNull MutableGraph<TyckOrder> graph, @NotNull TyckOrder unit) {
    return hasSuc(graph, MutableSet.create(), unit, unit);
  }

  public static class SCCTyckingFailed extends InterruptException {
    public final @NotNull ImmutableSeq<TyckOrder> what;
    public SCCTyckingFailed(@NotNull ImmutableSeq<TyckOrder> what) { this.what = what; }
    @Override public InterruptStage stage() { return InterruptStage.Tycking; }
  }
}
