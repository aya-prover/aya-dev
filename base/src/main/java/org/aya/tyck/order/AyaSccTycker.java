// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.order;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableSet;
import org.aya.concrete.stmt.decl.Decl;
import org.aya.concrete.stmt.decl.DeclInfo;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.def.Def;
import org.aya.core.def.FnDef;
import org.aya.core.def.GenericDef;
import org.aya.core.def.UserDef;
import org.aya.core.term.Callable;
import org.aya.core.term.Term;
import org.aya.generic.util.InterruptException;
import org.aya.resolve.ResolveInfo;
import org.aya.terck.BadRecursion;
import org.aya.terck.CallResolver;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.StmtTycker;
import org.aya.tyck.error.CounterexampleError;
import org.aya.tyck.error.TyckOrderError;
import org.aya.tyck.trace.Trace;
import org.aya.util.reporter.BufferReporter;
import org.aya.util.reporter.CollectingReporter;
import org.aya.util.reporter.CountingReporter;
import org.aya.util.reporter.Reporter;
import org.aya.util.terck.CallGraph;
import org.aya.util.terck.MutableGraph;
import org.aya.util.tyck.SCCTycker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.function.Function;

/**
 * Tyck statements in SCC.
 *
 * @author kiva
 * @see ExprTycker
 */
public record AyaSccTycker(
  @NotNull StmtTycker tycker,
  @NotNull CountingReporter reporter,
  @NotNull ResolveInfo resolveInfo,
  @NotNull MutableList<@NotNull GenericDef> wellTyped,
  @NotNull MutableMap<Decl.TopLevel, CollectingReporter> sampleReporters
) implements SCCTycker<TyckOrder, AyaSccTycker.SCCTyckingFailed> {
  public static @NotNull AyaSccTycker create(ResolveInfo resolveInfo, @Nullable Trace.Builder builder, @NotNull Reporter outReporter) {
    var counting = CountingReporter.delegate(outReporter);
    return new AyaSccTycker(new StmtTycker(counting, builder), counting, resolveInfo, MutableList.create(), MutableMap.create());
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
    var unit = scc.view().map(TyckOrder::unit).distinct().toImmutableSeq();
    // the flattened dependency graph (FDG) lose information about header order, in other words,
    // FDG treats all order as body order, so it allows all kinds of mutual recursion to be generated.
    // To detect circular dependency in signatures which we forbid, we have to apply the old way,
    // that is, what we did before https://github.com/aya-prover/aya-dev/pull/326
    var headerOrder = headerOrder(scc, unit);
    if (headerOrder.sizeEquals(1)) {
      checkUnit(new TyckOrder.Body(headerOrder.first()));
    } else {
      var tyckTasks = headerOrder.view()
        .<TyckOrder>map(TyckOrder.Head::new)
        .appendedAll(headerOrder.map(TyckOrder.Body::new))
        .toImmutableSeq();
      tyckTasks.forEach(this::check);
      terck(tyckTasks.view());
    }
  }

  /**
   * Generate the order of dependency of headers, fail if a cycle occurs.
   *
   * @author re-xyr, kiva
   */
  public @NotNull ImmutableSeq<TyckUnit> headerOrder(@NotNull ImmutableSeq<TyckOrder> forError, @NotNull ImmutableSeq<TyckUnit> stmts) {
    var graph = MutableGraph.<TyckUnit>create();
    stmts.forEach(stmt -> {
      var reference = MutableList.<TyckUnit>create();
      new SigRefFinder(reference).accept(stmt);
      var filter = reference.filter(unit -> unit.needTyck(resolveInfo.thisModule().moduleName()));
      // If your telescope uses yourself, you should reject the function. --- ice1000
      // note: just check direct references, indirect ones will be checked using topological order
      if (filter.contains(stmt)) {
        reporter.report(new TyckOrderError.SelfReference(stmt));
        throw new SCCTyckingFailed(forError);
      }
      graph.sucMut(stmt).appendAll(filter);
    });
    var order = graph.topologicalOrder();
    var cycle = order.filter(s -> s.sizeGreaterThan(1));
    if (cycle.isNotEmpty()) {
      cycle.forEach(c -> reporter.report(new TyckOrderError.CircularSignature(c)));
      throw new SCCTyckingFailed(forError);
    }
    return order.flatMap(Function.identity());
  }

  private void checkUnit(@NotNull TyckOrder order) {
    if (order instanceof TyckOrder.Body && order.unit() instanceof TeleDecl.FnDecl fn && fn.body.isLeft()) {
      checkSimpleFn(order, fn);
    } else {
      check(order);
      if (order instanceof TyckOrder.Body body)
        terck(SeqView.of(body));
    }
  }

  private <T> boolean hasSuc(
    @NotNull MutableGraph<T> G,
    @NotNull MutableSet<T> book,
    @NotNull T vertex,
    @NotNull T suc
  ) {
    if (book.contains(vertex)) return false;
    book.add(vertex);
    for (var test : G.suc(vertex)) {
      if (test.equals(suc)) return true;
      if (hasSuc(G, book, test, suc)) return true;
    }
    return false;
  }

  private <T> boolean selfReferencing(@NotNull MutableGraph<T> graph, @NotNull T unit) {
    return hasSuc(graph, MutableSet.create(), unit, unit);
  }

  private void checkSimpleFn(@NotNull TyckOrder order, @NotNull TeleDecl.FnDecl fn) {
    if (selfReferencing(resolveInfo.depGraph(), order)) {
      reporter.report(new BadRecursion(fn.sourcePos(), fn.ref, null));
      throw new SCCTyckingFailed(ImmutableSeq.of(order));
    }
    decideTyckResult(fn, fn, tycker.simpleFn(reuseTopLevel(fn), fn));
    if (reporter.anyError()) throw new SCCTyckingFailed(ImmutableSeq.of(order));
  }

  private void check(@NotNull TyckOrder tyckOrder) {
    switch (tyckOrder) {
      case TyckOrder.Head head -> checkHeader(tyckOrder, head.unit());
      case TyckOrder.Body body -> checkBody(tyckOrder, body.unit());
    }
  }

  private void checkHeader(@NotNull TyckOrder order, @NotNull TyckUnit stmt) {
    if (stmt instanceof Decl decl) tycker.tyckHeader(decl, reuse(decl));
    if (reporter.anyError()) throw new SCCTyckingFailed(ImmutableSeq.of(order));
  }

  private void checkBody(@NotNull TyckOrder order, @NotNull TyckUnit stmt) {
    if (stmt instanceof Decl decl) {
      var def = tycker.tyck(decl, reuse(decl));
      if (decl instanceof Decl.TopLevel topLevel) decideTyckResult(decl, topLevel, def);
    }
    if (reporter.anyError()) throw new SCCTyckingFailed(ImmutableSeq.of(order));
  }

  private void decideTyckResult(@NotNull Decl decl, @NotNull Decl.TopLevel proof, @NotNull GenericDef def) {
    assert decl == proof;
    switch (proof.personality()) {
      case NORMAL -> {
        wellTyped.append(def);
        resolveInfo.shapeFactory().bonjour(def);
      }
      case COUNTEREXAMPLE -> {
        var sampleReporter = sampleReporters.getOrPut(proof, BufferReporter::new);
        var problems = sampleReporter.problems().toImmutableSeq();
        if (problems.isEmpty()) reporter.report(new CounterexampleError(decl.sourcePos(), decl.ref()));
        if (def instanceof UserDef<?> userDef) userDef.problems = problems;
      }
    }
  }

  private @NotNull ExprTycker reuse(@NotNull Decl decl) {
    // IDEA says the match is not exhaustive, but it is.
    return switch (decl) {
      case Decl.TopLevel topLevel -> reuseTopLevel(topLevel);
      case TeleDecl.DataCtor ctor -> reuseTopLevel(ctor.dataRef.concrete);
      case TeleDecl.StructField field -> reuseTopLevel(field.structRef.concrete);
    };
  }

  private @NotNull ExprTycker reuseTopLevel(@NotNull Decl.TopLevel decl) {
    // prevent counterexample errors from being reported to the user reporter
    if (decl.personality() == DeclInfo.Personality.COUNTEREXAMPLE) {
      var reporter = sampleReporters.getOrPut(decl, BufferReporter::new);
      return new ExprTycker(resolveInfo.primFactory(), resolveInfo.shapeFactory(), reporter, tycker.traceBuilder);
    }
    return resolveInfo.newTycker(reporter, tycker.traceBuilder);
  }

  private void terck(@NotNull SeqView<TyckOrder> units) {
    var recDefs = units.filterIsInstance(TyckOrder.Body.class)
      .filter(u -> selfReferencing(resolveInfo.depGraph(), u))
      .map(TyckOrder::unit);
    if (recDefs.isEmpty()) return;
    // TODO: positivity check for data/record definitions
    var fn = recDefs.filterIsInstance(TeleDecl.FnDecl.class)
      .map(f -> f.ref.core);
    terckRecursiveFn(fn);
  }

  private void terckRecursiveFn(@NotNull SeqView<FnDef> fn) {
    var targets = MutableSet.<Def>from(fn);
    if (targets.isEmpty()) return;
    var graph = CallGraph.<Callable, Def, Term.Param>create();
    fn.forEach(def -> new CallResolver(resolveInfo.primFactory(), def, targets, graph).accept(def));
    var bads = graph.findBadRecursion();
    bads.view()
      .sorted(Comparator.comparing(a -> a.matrix().domain().ref().concrete.sourcePos()))
      .forEach(f -> {
        var ref = f.matrix().domain().ref();
        reporter.report(new BadRecursion(ref.concrete.sourcePos(), ref, f));
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
