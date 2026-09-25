// Copyright (c) 2020-2026 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.states;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableArrayList;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableSet;
import kala.value.Value;
import org.aya.generic.AyaDocile;
import org.aya.generic.Instance;
import org.aya.generic.TermVisitor;
import org.aya.pretty.doc.Doc;
import org.aya.states.primitive.PrimFactory;
import org.aya.states.primitive.ShapeFactory;
import org.aya.syntax.core.annotation.Bound;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.term.FreeTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.MemberCall;
import org.aya.syntax.core.term.call.MetaCall;
import org.aya.syntax.core.term.xtt.CofNF;
import org.aya.syntax.core.term.xtt.DimTerm;
import org.aya.syntax.ref.LocalCtx;
import org.aya.syntax.ref.LocalVar;
import org.aya.syntax.ref.MetaVar;
import org.aya.tyck.error.MetaVarError;
import org.aya.unify.Unifier;
import org.aya.util.*;
import org.aya.util.position.SourcePos;
import org.aya.util.position.WithPos;
import org.aya.util.reporter.Reporter;
import org.aya.util.reporter.ThrowingReporter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public final class TyckState {
  private final @NotNull MutableList<Eqn> eqns = MutableList.create();
  private final @NotNull MutableList<WithPos<MetaVar>> activeMetas = MutableList.create();
  public final @NotNull MutableMap<MetaVar, Term> solutions = MutableMap.create();
  public final @NotNull ShapeFactory shapeFactory;
  public final @NotNull PrimFactory primFactory;
  public static final int I0 = 0;
  public static final int I1 = 1;

  private final @NotNull MutableMap<LocalVar, Integer> varToId = MutableMap.create();
  private int nextId = 2;

  private final @NotNull MutableList<Edge> edgeStack = MutableList.create();
  private final @NotNull MutableList<LocalVar> assumptions = MutableList.create();

  public record Edge(int u, int v) { }

  public TyckState(
    @NotNull ShapeFactory shapeFactory,
    @NotNull PrimFactory primFactory
  ) {
    this.shapeFactory = shapeFactory;
    this.primFactory = primFactory;
  }

  private int computeId(@NotNull Term term, boolean create) {
    return switch (term) {
      case FreeTerm(var v) -> {
        if (!varToId.containsKey(v)) {
          if (create) varToId.put(v, nextId++);
          else yield -1;
        }
        yield varToId.get(v);
      }
      case DimTerm dim -> switch (dim) {
        case I0 -> I0;
        case I1 -> I1;
      };
      default -> -1;
    };
  }

  private <T> boolean isTrueHelper(@NotNull CofNF.OrVar<T> cof, Predicate<T> pred) {
    return switch (cof) {
      case CofNF.IsVar(var v) -> assumptions.contains(v);
      case CofNF.Conc(var c) -> pred.test(c);
    };
  }

  public boolean isTrue(@NotNull CofNF.OrVar<CofNF.Disj> conjCof) {
    return isTrueHelper(conjCof, disj -> disj.elements().anyMatch(this::isTrueConj));
  }

  private boolean isTrueConj(@NotNull CofNF.OrVar<CofNF.Conj> conjCof) {
    return isTrueHelper(conjCof, conj -> conj.elements().allMatch(this::isTrueEq));
  }

  private boolean isTrueEq(@NotNull CofNF.OrVar<CofNF.EqCofTerm> eqCof) {
    return isTrueHelper(eqCof, cof -> isConnected(cof.lhs(), cof.rhs()));
  }

  public boolean isConnected(@NotNull Term lhs, @NotNull Term rhs) {
    // First one set it to true in case the lhs and rhs are the same but not in the table
    var l = computeId(lhs, true);
    var r = computeId(rhs, false);
    if (l < 0 || r < 0) return false;
    if (l == r) return true;

    // Rebuild Union-Find from the edge stack on demand
    var uf = new UnionFind(nextId);
    for (Edge edge : edgeStack) {
      uf.union(edge.u(), edge.v());
    }
    return uf.find(l) == uf.find(r);
  }

  public void connect(@NotNull Term lhs, @NotNull Term rhs) {
    var l = computeId(lhs, true);
    var r = computeId(rhs, true);
    if (l < 0 || r < 0) throw new Panic("Unsupported connection, need error report");
    edgeStack.append(new Edge(l, r));
  }

  public void disconnect(@NotNull Term lhs, @NotNull Term rhs) {
    var l = computeId(lhs, false);
    var r = computeId(rhs, false);
    if (l < 0 || r < 0) return;

    for (int i = edgeStack.size() - 1; i >= 0; i--) {
      var edge = edgeStack.get(i);
      if (edge.u() == l && edge.v() == r) {
        edgeStack.removeAt(i);
        return;
      }
    }
    throw new Panic("Trying to disconnect a non-existing connection, panic");
  }

  public void assume(LocalVar v) { assumptions.append(v); }
  public void unassume(LocalVar v) { assumptions.remove(v); }
  public void removeConnection(@NotNull LocalVar var) { varToId.remove(var); }

  public @NotNull ImmutableSeq<Term> buildCofTerms() {
    var ret = MutableArrayList.<Term>create(assumptions.size() + edgeStack.size());
    for (var v : assumptions) ret.append(new FreeTerm(v));
    var idToVar = new LocalVar[nextId];
    varToId.forEach((var, id) -> idToVar[id] = var);
    for (var edge : edgeStack) {
      var eqTerm = new CofNF.EqCofTerm(new FreeTerm(idToVar[edge.u()]), new FreeTerm(idToVar[edge.v()]));
      ret.append(new CofNF.Disj(new CofNF.Conj(eqTerm)));
    }
    return ret.toSeq();
  }

  @ApiStatus.Internal
  public void solve(MetaVar meta, Term candidate) { solutions.put(meta, candidate); }

  @ApiStatus.Internal
  public @NotNull Decision solveEqn(@NotNull Reporter reporter, @NotNull Eqn eqn, boolean allowDelay) {
    var unifier = new Unifier(this, eqn.localCtx, reporter, eqn.pos, eqn.cmp, allowDelay);
    // We're at the end of the type checking, let's solve something that we didn't want to solve before
    if (!allowDelay) unifier.allowVague = true;
    return unifier.checkEqn(eqn);
  }

  public void solveMetas(@NotNull Reporter reporter) {
    int postSimplificationSize = -1;
    // equations that are solved by nonstandard methods
    var evilEqns = MutableList.<Eqn>create();
    while (eqns.isNotEmpty()) {
      //noinspection StatementWithEmptyBody
      while (simplify(reporter)) ;
      var frozenEqns = eqns.toSeq();
      // are we making progress?
      if (postSimplificationSize == frozenEqns.size()) {
        reporter.report(new MetaVarError.CannotSolveEquations(frozenEqns));
        return;
      } else postSimplificationSize = frozenEqns.size();
      // If the standard 'pattern' fragment cannot solve all equations, try to use a nonstandard method
      if (frozenEqns.isNotEmpty()) for (var eqn : frozenEqns) {
        if (solveEqn(reporter, eqn, false) == Decision.YES) evilEqns.append(eqn);
      }
    }
    if (evilEqns.isNotEmpty()) {
      reporter.report(new MetaVarError.DidSomethingBad(evilEqns.toImmutableArray()));
    }
  }

  public @NotNull Term computeSolution(
    @Closed @NotNull MetaCall meta,
    @Bound @NotNull MetaVar.OfType.ClassType classType,
    @NotNull UnaryOperator<@Closed Term> f
  ) {
    var insted = classType.instTele(meta.args().view());
    var available = insted.instances().mapNotNull(it -> switch (it) {
      // try replace all param of [def] with meta, then solve by unify with [insted.type()]
      case Instance.Global(var def) -> {
        var tele = def.signature();

        // TODO: how
        throw new UnsupportedOperationException("TODO");
      }
      case Instance.Local(var ref, @Closed var ty) -> {
        // ctx ⊢ meta.args()
        // and
        // ty consists of meta.args() and top-level things, thus
        // ctx ⊢ ty
        var ctx = classType.localCtx();
        // I guess this won't cause infinite recursion, as the context of `meta` doesn't contain itself
        // I guess we can safely ignore the problems, as we are "trying" to compare, not "requiring" them to equal.
        // Thus `sourcePos` is also safe to be `SourcePos.NONE`
        var someUnifier = new Unifier(this, ctx, new ThrowingReporter(Value.lazy(Panic::unreachable)), SourcePos.NONE, Ordering.Eq, false);
        someUnifier.instanceFilteringMode();

        var required = insted.type();
        for (int i = 0; i < required.args().size(); i++) {
          var field = required.ref().members().get(i);
          var proj = MemberCall.make(ty, ref, field, 0, ImmutableSeq.empty());
          var projTy = field.signature().makePi();
          // Keep the unsure
          if (someUnifier.compare(required.args().get(i).apply(ref), proj, projTy) == Decision.NO) {
            yield null;
          }
        }
        yield ref;
      }
    });

    if (available.sizeEquals(1)) {
      return available.getAny();
    } else {
      return meta;
    }
  }

  public @Closed @NotNull Term computeSolution(@Closed @NotNull MetaCall meta, @NotNull UnaryOperator<@Closed Term> f) {
    return solutions.getOption(meta.ref())
      .map(sol -> f.apply(MetaCall.app(sol, meta.args(), meta.ref().ctxSize())))
      .getOrElse(() -> {
        if (!(meta.ref().req() instanceof MetaVar.OfType.ClassType classType)) return meta;
        return computeSolution(meta, classType, f);
      });
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

  public void addEqn(@Closed Eqn eqn) {
    eqns.append(eqn);
    var currentActiveMetas = activeMetas.size();
    var consumer = new Consumer<Term>() {
      @Override public void accept(Term term) {
        if (term instanceof MetaCall hole && !solutions.containsKey(hole.ref()))
          activeMetas.append(new WithPos<>(eqn.pos, hole.ref()));
        term.descent(TermVisitor.of(tm -> {
          accept(tm);
          return tm;
        }));
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
