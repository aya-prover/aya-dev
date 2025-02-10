// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableStack;
import org.aya.generic.AyaDocile;
import org.aya.pretty.doc.Doc;
import org.aya.primitive.PrimFactory;
import org.aya.primitive.ShapeFactory;
import org.aya.syntax.core.term.FreeTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.MetaCall;
import org.aya.syntax.core.term.xtt.DimTerm;
import org.aya.syntax.ref.LocalCtx;
import org.aya.syntax.ref.LocalVar;
import org.aya.syntax.ref.MetaVar;
import org.aya.tyck.error.MetaVarError;
import org.aya.unify.Unifier;
import org.aya.util.DynamicForest;
import org.aya.util.Ordering;
import org.aya.util.Panic;
import org.aya.util.PrettierOptions;
import org.aya.util.position.SourcePos;
import org.aya.util.position.WithPos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public final class TyckState {
  private final @NotNull MutableList<Eqn> eqns = MutableList.create();
  private final @NotNull MutableList<WithPos<MetaVar>> activeMetas = MutableList.create();
  public final @NotNull MutableMap<MetaVar, Term> solutions = MutableMap.create();
  public final @NotNull MutableStack<LocalVar> classThis = MutableStack.create();
  public final @NotNull ShapeFactory shapeFactory;
  public final @NotNull PrimFactory primFactory;
  private final @NotNull MutableMap<LocalVar, DynamicForest.Handle> connections = MutableMap.create();

  public static final DynamicForest.Handle I0 = DynamicForest.create();
  public static final DynamicForest.Handle I1 = DynamicForest.create();

  public TyckState(@NotNull ShapeFactory shapeFactory, @NotNull PrimFactory primFactory) {
    this.shapeFactory = shapeFactory;
    this.primFactory = primFactory;
  }

  private @Nullable DynamicForest.Handle computeHandle(@NotNull Term term, boolean create) {
    return switch (term) {
      case FreeTerm(var v) -> create ? connections.getOrPut(v, DynamicForest::create) : connections.getOrNull(v);
      case DimTerm dim -> switch (dim) {
        case I0 -> I0;
        case I1 -> I1;
      };
      default -> null;
    };
  }

  public boolean isConnected(@NotNull Term lhs, @NotNull Term rhs) {
    var l = computeHandle(lhs, true);
    var r = computeHandle(rhs, true);
    if (l == null || r == null) return false;
    return l.isConnected(r);
  }

  public void connect(@NotNull Term lhs, @NotNull Term rhs) {
    var l = computeHandle(lhs, true);
    var r = computeHandle(rhs, true);
    if (l == null || r == null) throw new Panic("Unsupported connection, need error report");
    l.connect(r);
  }

  public void disconnect(@NotNull Term lhs, @NotNull Term rhs) {
    var l = computeHandle(lhs, false);
    var r = computeHandle(rhs, false);
    if (l != null && r != null) l.disconnect(r);
  }

  public void removeConnection(@NotNull LocalVar var) { connections.remove(var); }

  @ApiStatus.Internal
  public void solve(MetaVar meta, Term candidate) { solutions.put(meta, candidate); }

  @ApiStatus.Internal
  public boolean solveEqn(@NotNull Reporter reporter, @NotNull Eqn eqn, boolean allowDelay) {
    var unifier = new Unifier(this, eqn.localCtx, reporter, eqn.pos, eqn.cmp, allowDelay);
    // We're at the end of the type checking, let's solve something that we didn't want to solve before
    if (!allowDelay) unifier.allowVague = true;
    return unifier.checkEqn(eqn);
  }

  public void solveMetas(@NotNull Reporter reporter) {
    int postSimplificationSize = -1;
    var evilEqns = MutableList.<Eqn>create();
    while (eqns.isNotEmpty()) {
      //noinspection StatementWithEmptyBody
      while (simplify(reporter)) ;
      var frozenEqns = eqns.toImmutableSeq();
      if (postSimplificationSize == frozenEqns.size()) {
        // TODO: report error, cannot solve eqns
        reporter.report(new MetaVarError.CannotSolveEquations(frozenEqns));
        return;
      } else postSimplificationSize = frozenEqns.size();
      // If the standard 'pattern' fragment cannot solve all equations, try to use a nonstandard method
      if (frozenEqns.isNotEmpty()) for (var eqn : frozenEqns) {
        if (solveEqn(reporter, eqn, false)) evilEqns.append(eqn);
      }
    }
    if (evilEqns.isNotEmpty()) {
      reporter.report(new MetaVarError.DidSomethingBad(evilEqns.toImmutableArray()));
    }
  }

  public @NotNull Term computeSolution(@NotNull MetaCall meta, @NotNull UnaryOperator<Term> f) {
    return solutions.getOption(meta.ref())
      .map(sol -> f.apply(MetaCall.app(sol, meta.args(), meta.ref().ctxSize())))
      .getOrDefault(meta);
  }

  /** @return true if <code>this.eqns</code> and <code>this.activeMetas</code> are mutated. */
  private boolean simplify(@NotNull Reporter reporter) {
    var removingMetas = MutableList.<WithPos<MetaVar>>create();
    for (var activeMeta : activeMetas) {
      var v = activeMeta.data();
      if (solutions.containsKey(v)) {
        eqns.retainIf(eqn -> {
          // If the blocking meta is solved, we can check again
          if (eqn.lhs.ref() == v) {
            solveEqn(reporter, eqn, true);
            return false;
          } else return true;
        });
        removingMetas.append(activeMeta);
      }
    }
    activeMetas.removeIf(removingMetas::contains);
    return removingMetas.isNotEmpty();
  }

  public void addEqn(Eqn eqn) {
    eqns.append(eqn);
    var currentActiveMetas = activeMetas.size();
    var consumer = new Consumer<Term>() {
      @Override public void accept(Term term) {
        if (term instanceof MetaCall hole && !solutions.containsKey(hole.ref()))
          activeMetas.append(new WithPos<>(eqn.pos, hole.ref()));
        term.descent(tm -> {
          accept(tm);
          return tm;
        });
      }
    };
    consumer.accept(eqn.lhs);
    consumer.accept(eqn.rhs);
    assert activeMetas.sizeGreaterThan(currentActiveMetas) : "Adding a bad equation";
  }

  public void clearTmp() {
    eqns.clear();
    activeMetas.clear();
    solutions.clear();
  }

  public record Eqn(
    @NotNull MetaCall lhs, @NotNull Term rhs, @Nullable Term type,
    @NotNull Ordering cmp, @NotNull SourcePos pos,
    @NotNull LocalCtx localCtx
  ) implements AyaDocile {
    public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
      return Doc.stickySep(lhs.toDoc(options), Doc.symbol(cmp.symbol), rhs.toDoc(options));
    }
  }
}
