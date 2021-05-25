// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.unify;

import org.aya.api.error.Reporter;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.Var;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.Decl;
import org.aya.core.def.DataDef;
import org.aya.core.def.Def;
import org.aya.core.sort.LevelSubst;
import org.aya.core.sort.Sort;
import org.aya.core.term.*;
import org.aya.core.visitor.Substituter;
import org.aya.core.visitor.Unfolder;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.error.HoleProblem;
import org.aya.tyck.error.RecursiveSolutionError;
import org.aya.tyck.trace.Trace;
import org.aya.util.Decision;
import org.aya.util.Ordering;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author re-xyr
 * @apiNote Use {@link UntypedDefEq#compare(Term, Term)} instead of visiting directly!
 */
public record UntypedDefEq(
  @NotNull TypedDefEq defeq, @NotNull Ordering cmp
) implements Term.Visitor<@NotNull Term, @Nullable Term> {
  public @Nullable Term compare(@NotNull Term lhs, @NotNull Term rhs) {
    final var x = lhs.accept(this, rhs);
    return x != null ? x.normalize(NormalizeMode.WHNF) : null;
  }

  @Override public void traceEntrance(@NotNull Term lhs, @NotNull Term rhs) {
    defeq.traceEntrance(new Trace.UnifyT(lhs, rhs, defeq.pos));
  }

  @Override public void traceExit(@Nullable Term term) {
    defeq.traceExit(true);
  }

  @Override public @Nullable Term visitRef(@NotNull RefTerm lhs, @NotNull Term preRhs) {
    if (preRhs instanceof RefTerm rhs
      && defeq.varSubst.getOrDefault(rhs.var(), rhs).var() == lhs.var()) {
      return rhs.type();
    }
    return null;
  }

  @Override public @Nullable Term visitApp(@NotNull ElimTerm.App lhs, @NotNull Term preRhs) {
    if (!(preRhs.normalize(NormalizeMode.WHNF) instanceof ElimTerm.App rhs)) return null;
    var preFnType = compare(lhs.of(), rhs.of());
    if (!(preFnType instanceof FormTerm.Pi fnType)) return null;
    if (!defeq.compare(lhs.arg().term(), rhs.arg().term(), fnType.param().type())) return null;
    return fnType.substBody(lhs.arg().term());
  }

  @Override public @Nullable Term visitAccess(CallTerm.@NotNull Access lhs, @NotNull Term preRhs) {
    if (!(preRhs instanceof CallTerm.Access rhs)) return null;
    var preStructType = compare(lhs.of(), rhs.of());
    if (!(preStructType instanceof CallTerm.Struct structType)) return null;
    if (lhs.ref() != rhs.ref()) return null;
    return Def.defResult(lhs.ref());
  }

  @Override public @Nullable Term visitProj(@NotNull ElimTerm.Proj lhs, @NotNull Term preRhs) {
    if (!(preRhs instanceof ElimTerm.Proj rhs)) return null;
    var preTupType = compare(lhs.of(), rhs.of());
    if (!(preTupType instanceof FormTerm.Sigma tupType)) return null;
    if (lhs.ix() != rhs.ix()) return null;
    var params = tupType.params();
    for (int i = 1; i < lhs.ix(); i++) {
      var l = new ElimTerm.Proj(lhs, i);
      var currentParam = params.first();
      params = params.drop(1).map(x -> x.subst(currentParam.ref(), l));
    }
    if (params.isNotEmpty()) return params.first().type();
    return params.last().type();
  }

  private @Nullable Term extract(CallTerm.@NotNull Hole lhs, Term rhs) {
    var subst = new Substituter.TermSubst(new MutableHashMap<>(/*spine.size() * 2*/));
    var meta = lhs.ref().core();
    for (var arg : lhs.args().view().zip(meta.telescope)) {
      if (arg._1.term() instanceof RefTerm ref) {
        // TODO[xyr]: do scope checking here
        subst.add(ref.var(), arg._2.toTerm());
        if (rhs == null) return null;
      } else return null;
      // TODO[ice]: ^ eta var
    }
    subst.add(Unfolder.buildSubst(meta.contextTele, lhs.contextArgs()));
    defeq.varSubst.forEach(subst::add);
    return rhs.subst(subst);
  }

  @Override public @Nullable Term visitHole(CallTerm.@NotNull Hole lhs, @NotNull Term rhs) {
    var meta = lhs.ref().core();
    if (rhs instanceof CallTerm.Hole rcall && lhs.ref() == rcall.ref()) {
      var holeTy = FormTerm.Pi.make(false, meta.telescope, meta.result);
      for (var arg : lhs.args().view().zip(rcall.args())) {
        if (!(holeTy instanceof FormTerm.Pi holePi))
          throw new IllegalStateException("meta arg size larger than param size. this should not happen");
        if (!defeq.compare(arg._1.term(), arg._2.term(), holePi.param().type())) return null;
        holeTy = holePi.substBody(arg._1.term());
      }
      return holeTy;
    }
    var solved = extract(lhs, rhs);
    if (solved == null) {
      reporter().report(new HoleProblem.BadSpineError(lhs, defeq.pos));
      return null;
    }
    assert meta.body == null;
    compare(solved.computeType(), meta.result);
    var scopeCheck = solved.scopeCheck(meta.fullTelescope().map(Term.Param::ref).toImmutableSeq());
    if (scopeCheck.isNotEmpty()) {
      reporter().report(new HoleProblem.BadlyScopedError(lhs, solved, scopeCheck, defeq.pos));
      return null;
    }
    var success = meta.solve(lhs.ref(), solved);
    if (!success) {
      reporter().report(new RecursiveSolutionError(lhs.ref(), solved, defeq.pos));
      throw new ExprTycker.TyckInterruptedException();
    }
    return meta.result;
  }

  private @NotNull Reporter reporter() {
    return defeq.tycker.reporter;
  }

  @Override public @Nullable Term visitPi(@NotNull FormTerm.Pi lhs, @NotNull Term preRhs) {
    if (!(preRhs.normalize(NormalizeMode.WHNF) instanceof FormTerm.Pi rhs)) return null;
    return defeq.checkParam(lhs.param(), rhs.param(), FormTerm.Univ.OMEGA, () -> null, () -> {
      var bodyIsOk = defeq.compare(lhs.body(), rhs.body(), FormTerm.Univ.OMEGA);
      if (!bodyIsOk) return null;
      return FormTerm.Univ.OMEGA;
    });
  }

  @Override public @Nullable Term visitSigma(@NotNull FormTerm.Sigma lhs, @NotNull Term preRhs) {
    if (!(preRhs.normalize(NormalizeMode.WHNF) instanceof FormTerm.Sigma rhs)) return null;
    return defeq.checkParams(lhs.params(), rhs.params(), () -> null, () -> {
      var bodyIsOk = defeq.compare(lhs.params().last().type(), rhs.params().last().type(), FormTerm.Univ.OMEGA);
      if (!bodyIsOk) return null;
      return FormTerm.Univ.OMEGA;
    });
  }

  @Override public @Nullable Term visitUniv(@NotNull FormTerm.Univ lhs, @NotNull Term preRhs) {
    if (!(preRhs.normalize(NormalizeMode.WHNF) instanceof FormTerm.Univ rhs)) return null;
    defeq.tycker.equations.add(lhs.sort(), rhs.sort(), cmp, defeq.pos);
    return new FormTerm.Univ((cmp == Ordering.Lt ? lhs.sort() : rhs.sort()).succ(1));
  }

  private static Term unreachable() {
    throw new IllegalStateException();
  }

  @Override public @NotNull Term visitTup(@NotNull IntroTerm.Tuple lhs, @NotNull Term preRhs) {
    return unreachable();
  }

  @Override public @NotNull Term visitNew(@NotNull IntroTerm.New newTerm, @NotNull Term term) {
    return unreachable();
  }

  @NotNull LevelSubst levels(
    @NotNull DefVar<? extends Def, ? extends Decl> def,
    ImmutableSeq<Sort.@NotNull CoreLevel> l, ImmutableSeq<Sort.@NotNull CoreLevel> r
  ) {
    var levelSubst = new LevelSubst.Simple(MutableMap.of());
    for (var levels : l.zip(r).zip(Def.defLevels(def))) {
      defeq.tycker.equations.add(levels._1._1, levels._1._2, cmp, defeq.pos);
      levelSubst.solution().put(levels._2, levels._1._1);
    }
    return levelSubst;
  }

  @Override public @Nullable Term visitFnCall(@NotNull CallTerm.Fn lhs, @NotNull Term preRhs) {
    var substMap = MutableMap.<Var, Term>of();
    for (var pa : lhs.args().view().zip(lhs.ref().core.telescope().view())) {
      substMap.set(pa._2.ref(), pa._1.term());
    }
    var retType = lhs.ref().core.result().subst(substMap);
    if (!(preRhs instanceof CallTerm.Fn rhs) || lhs.ref() != rhs.ref()) {
      if ((lhs.whnf() != Decision.YES || preRhs.whnf() != Decision.YES) && defeq.compareWHNF(lhs, preRhs, retType))
        return retType;
      else return null;
    }
    // Lossy comparison
    var subst = levels(lhs.ref(), lhs.sortArgs(), rhs.sortArgs());
    if (defeq.visitArgs(lhs.args(), rhs.args(), Term.Param.subst(Def.defTele(lhs.ref()), subst))) return retType;
    if (defeq.compareWHNF(lhs, rhs, retType)) return retType;
    else return null;
  }

  @Override public @Nullable Term visitDataCall(@NotNull CallTerm.Data lhs, @NotNull Term preRhs) {
    if (!(preRhs.normalize(NormalizeMode.WHNF) instanceof CallTerm.Data rhs) || lhs.ref() != rhs.ref()) return null;
    var subst = levels(lhs.ref(), lhs.sortArgs(), rhs.sortArgs());
    var args = defeq.visitArgs(lhs.args(), rhs.args(), Term.Param.subst(Def.defTele(lhs.ref()), subst));
    // Do not need to be computed precisely because unification won't need this info
    return args ? FormTerm.Univ.OMEGA : null;
  }

  @Override public @Nullable Term visitStructCall(@NotNull CallTerm.Struct lhs, @NotNull Term preRhs) {
    if (!(preRhs.normalize(NormalizeMode.WHNF) instanceof CallTerm.Struct rhs) || lhs.ref() != rhs.ref()) return null;
    var subst = levels(lhs.ref(), lhs.sortArgs(), rhs.sortArgs());
    var args = defeq.visitArgs(lhs.args(), rhs.args(), Term.Param.subst(Def.defTele(lhs.ref()), subst));
    return args ? FormTerm.Univ.OMEGA : null;
  }

  @Override public @Nullable Term visitPrimCall(CallTerm.@NotNull Prim lhs, @NotNull Term preRhs) {
    var substMap = MutableMap.<Var, Term>of();
    for (var pa : lhs.args().view().zip(lhs.ref().core.telescope().view())) {
      substMap.set(pa._2.ref(), pa._1.term());
    }
    var retType = lhs.ref().core.result().subst(substMap);
    if (!(preRhs instanceof CallTerm.Prim rhs) || lhs.ref() != rhs.ref()) {
      if ((lhs.whnf() != Decision.YES || preRhs.whnf() != Decision.YES) && defeq.compareWHNF(lhs, preRhs, retType))
        return retType;
      else return null;
    }
    // Lossy comparison
    var subst = levels(lhs.ref(), lhs.sortArgs(), rhs.sortArgs());
    if (defeq.visitArgs(lhs.args(), rhs.args(), Term.Param.subst(Def.defTele(lhs.ref()), subst))) return retType;
    if (defeq.compareWHNF(lhs, rhs, retType)) return retType;
    else return null;
  }

  @Override public @Nullable Term visitConCall(@NotNull CallTerm.Con lhs, @NotNull Term preRhs) {
    var substMap = MutableMap.<Var, Term>of();
    for (var pa : lhs.args().view().zip(lhs.ref().core.telescope().view())) {
      substMap.set(pa._2.ref(), pa._1.term());
    }
    var retType = lhs.ref().core.result().subst(substMap);
    if (!(preRhs instanceof CallTerm.Con rhs) || lhs.ref() != rhs.ref()) {
      if ((lhs.whnf() != Decision.YES || preRhs.whnf() != Decision.YES) && defeq.compareWHNF(lhs, preRhs, retType))
        return retType;
      else return null;
    }
    // Lossy comparison
    var subst = levels(lhs.head().dataRef(), lhs.sortArgs(), rhs.sortArgs());
    if (defeq.visitArgs(lhs.conArgs(), rhs.conArgs(), Term.Param.subst(DataDef.Ctor.conTele(lhs.ref()), subst)))
      return retType;
    return null;
  }

  @Override public @NotNull Term visitLam(@NotNull IntroTerm.Lambda lhs, @NotNull Term preRhs) {
    return unreachable();
  }
}
