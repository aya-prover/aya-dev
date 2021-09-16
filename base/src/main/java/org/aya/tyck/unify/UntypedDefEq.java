// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.unify;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.Var;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.stmt.Decl;
import org.aya.core.Meta;
import org.aya.core.def.CtorDef;
import org.aya.core.def.Def;
import org.aya.core.ops.Eta;
import org.aya.core.sort.LevelSubst;
import org.aya.core.sort.Sort;
import org.aya.core.term.*;
import org.aya.core.visitor.Substituter;
import org.aya.core.visitor.Unfolder;
import org.aya.tyck.error.HoleProblem;
import org.aya.tyck.trace.Trace;
import org.aya.util.Ordering;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author re-xyr
 * @apiNote Use {@link UntypedDefEq#compare(Term, Term)} instead of visiting directly!
 */
public record UntypedDefEq(
  @NotNull TypedDefEq defeq, @NotNull Ordering cmp){
  public @Nullable Term compare(@NotNull Term lhs, @NotNull Term rhs) {
    // lhs & rhs will both be WHNF if either is not a potentially reducible call
    if (TypedDefEq.isCall(lhs) || TypedDefEq.isCall(rhs)) {
      final var ty = doCompareUntyped(lhs, rhs);
      if (ty != null) return ty.normalize(NormalizeMode.WHNF);
    }
    lhs = lhs.normalize(NormalizeMode.WHNF);
    rhs = rhs.normalize(NormalizeMode.WHNF);
    final var x = doCompareUntyped(lhs, rhs);
    return x != null ? x.normalize(NormalizeMode.WHNF) : null;
  }

  @Nullable Term compareApprox(@NotNull Term preLhs, @NotNull Term preRhs) {
    //noinspection ConstantConditions
    return switch (preLhs) {
      case CallTerm.Fn lhs && preRhs instanceof CallTerm.Fn rhs -> lhs.ref() != rhs.ref() ? null : visitCall(lhs, rhs, lhs.ref());
      case CallTerm.Prim lhs && preRhs instanceof CallTerm.Prim rhs -> lhs.ref() != rhs.ref() ? null : visitCall(lhs, rhs, lhs.ref());
      default -> null;
    };
  }

  private @NotNull EqnSet.Eqn createEqn(@NotNull Term lhs, @NotNull Term rhs) {
    return new EqnSet.Eqn(lhs, rhs, cmp, defeq.pos, defeq.varSubst.toImmutableMap());
  }

  private Term doCompareUntyped(@NotNull Term type, @NotNull Term preRhs) {
    defeq.traceEntrance(new Trace.UnifyT(type.freezeHoles(defeq.levelEqns),
      preRhs.freezeHoles(defeq.levelEqns), defeq.pos));
    var ret = switch(type) {
      default -> throw new IllegalStateException();
      case @NotNull RefTerm lhs -> {
        if (preRhs instanceof RefTerm rhs
          && defeq.varSubst.getOrDefault(rhs.var(), rhs).var() == lhs.var()) {
          yield rhs.type();
        }
        yield null;
      }
      case @NotNull ElimTerm.App lhs -> {
        if (!(preRhs instanceof ElimTerm.App rhs)) yield null;
        var preFnType = compare(lhs.of(), rhs.of());
        if (!(preFnType instanceof FormTerm.Pi fnType)) yield null;
        if (!defeq.compare(lhs.arg().term(), rhs.arg().term(), fnType.param().type())) yield null;
        yield fnType.substBody(lhs.arg().term());
      }
      case @NotNull ElimTerm.Proj lhs -> {
        if (!(preRhs instanceof ElimTerm.Proj rhs)) yield null;
        var preTupType = compare(lhs.of(), rhs.of());
        if (!(preTupType instanceof FormTerm.Sigma tupType)) yield null;
        if (lhs.ix() != rhs.ix()) yield null;
        var params = tupType.params();
        for (int i = 1; i < lhs.ix(); i++) {
          var l = new ElimTerm.Proj(lhs, i);
          var currentParam = params.first();
          params = params.view().drop(1)
            .map(x -> x.subst(currentParam.ref(), l)).toImmutableSeq();
        }
        if (params.isNotEmpty()) yield params.first().type();
        yield params.last().type();
      }
      case @NotNull ErrorTerm term -> ErrorTerm.typeOf(term.freezeHoles(defeq.levelEqns));
      case @NotNull FormTerm.Pi lhs -> {
        if (!(preRhs instanceof FormTerm.Pi rhs)) yield null;
        yield defeq.checkParam(lhs.param(), rhs.param(), FormTerm.Univ.OMEGA, () -> null, () -> {
          var bodyIsOk = defeq.compare(lhs.body(), rhs.body(), FormTerm.Univ.OMEGA);
          if (!bodyIsOk) return null;
          return FormTerm.Univ.OMEGA;
        });
      }
      case @NotNull FormTerm.Sigma lhs -> {
        if (!(preRhs instanceof FormTerm.Sigma rhs)) yield null;
        yield defeq.checkParams(lhs.params(), rhs.params(), () -> null, () -> {
          var bodyIsOk = defeq.compare(lhs.params().last().type(), rhs.params().last().type(), FormTerm.Univ.OMEGA);
          if (!bodyIsOk) return null;
          return FormTerm.Univ.OMEGA;
        });
      }
      case @NotNull FormTerm.Univ lhs -> {
        if (!(preRhs instanceof FormTerm.Univ rhs)) yield null;
        defeq.levelEqns.add(lhs.sort(), rhs.sort(), cmp, defeq.pos);
        yield new FormTerm.Univ((cmp == Ordering.Lt ? lhs : rhs).sort().lift(1));
      }
      case @NotNull CallTerm.Fn lhs -> null;
      case @NotNull CallTerm.Data lhs -> {
        if (!(preRhs instanceof CallTerm.Data rhs) || lhs.ref() != rhs.ref()) yield null;
        var subst = levels(lhs.ref(), lhs.sortArgs(), rhs.sortArgs());
        var args = defeq.visitArgs(lhs.args(), rhs.args(), Term.Param.subst(Def.defTele(lhs.ref()), subst));
        // Do not need to be computed precisely because unification won't need this info
        yield args ? FormTerm.Univ.OMEGA : null;
      }
      case @NotNull CallTerm.Struct lhs -> {
        if (!(preRhs instanceof CallTerm.Struct rhs) || lhs.ref() != rhs.ref()) yield null;
        var subst = levels(lhs.ref(), lhs.sortArgs(), rhs.sortArgs());
        var args = defeq.visitArgs(lhs.args(), rhs.args(), Term.Param.subst(Def.defTele(lhs.ref()), subst));
        yield args ? FormTerm.Univ.OMEGA : null;
      }
      case @NotNull CallTerm.Con lhs -> {
        if (!(preRhs instanceof CallTerm.Con rhs) || lhs.ref() != rhs.ref()) yield null;
        var retType = getType(lhs, lhs.ref());
        // Lossy comparison
        var subst = levels(lhs.head().dataRef(), lhs.sortArgs(), rhs.sortArgs());
        if (defeq.visitArgs(lhs.conArgs(), rhs.conArgs(), Term.Param.subst(CtorDef.conTele(lhs.ref()), subst)))
          yield retType;
        yield null;
      }
      case CallTerm.@NotNull Prim lhs -> null;
      case CallTerm.@NotNull Access lhs -> {
        if (!(preRhs instanceof CallTerm.Access rhs)) yield null;
        var preStructType = compare(lhs.of(), rhs.of());
        if (!(preStructType instanceof CallTerm.Struct structType)) yield null;
        if (lhs.ref() != rhs.ref()) yield null;
        yield Def.defResult(lhs.ref());
      }
      case CallTerm.@NotNull Hole lhs -> {
        var meta = lhs.ref().core();
        if (preRhs instanceof CallTerm.Hole rcall && lhs.ref() == rcall.ref()) {
          var holeTy = FormTerm.Pi.make(meta.telescope, meta.result);
          for (var arg : lhs.args().view().zip(rcall.args())) {
            if (!(holeTy instanceof FormTerm.Pi holePi))
              throw new IllegalStateException("meta arg size larger than param size. this should not happen");
            if (!defeq.compare(arg._1.term(), arg._2.term(), holePi.param().type())) yield null;
            holeTy = holePi.substBody(arg._1.term());
          }
          yield holeTy;
        }
        var argSubst = extract(lhs, preRhs, meta);
        if (argSubst == null) {
          defeq.reporter.report(new HoleProblem.BadSpineError(lhs, defeq.pos));
          yield null;
        }
        var subst = Unfolder.buildSubst(meta.contextTele, lhs.contextArgs());
        // In this case, the solution may not be unique (see #608),
        // so we may delay its resolution to the end of the tycking when we disallow vague unification.
        if (!defeq.allowVague && subst.overlap(argSubst).anyMatch(var -> preRhs.findUsages(var) > 0)) {
          defeq.termEqns.addEqn(createEqn(lhs, preRhs));
          // Skip the unification and scope check
          yield meta.result;
        }
        subst.add(argSubst);
        defeq.varSubst.forEach(subst::add);
        var solved = preRhs.subst(subst);
        assert meta.body == null;
        compare(solved.computeType(), meta.result);
        var scopeCheck = solved.scopeCheck(meta.fullTelescope().map(Term.Param::ref).toImmutableSeq());
        if (scopeCheck.isNotEmpty()) {
          defeq.reporter.report(new HoleProblem.BadlyScopedError(lhs, solved, scopeCheck, defeq.pos));
          yield new ErrorTerm(solved);
        }
        if (!meta.solve(lhs.ref(), solved)) {
          defeq.reporter.report(new HoleProblem.RecursionError(lhs, solved, defeq.pos));
          yield new ErrorTerm(solved);
        }
        defeq.tracing(builder -> builder.append(new Trace.LabelT(defeq().pos, "Hole solved!")));
        yield meta.result;
      }
    };
    defeq.traceExit();
    return ret;
  }

  private @Nullable Substituter.TermSubst extract(
    @NotNull CallTerm.Hole lhs, @NotNull Term rhs, @NotNull Meta meta
  ) {
    var subst = new Substituter.TermSubst(new MutableHashMap<>(/*spine.size() * 2*/));
    for (var arg : lhs.args().view().zip(meta.telescope)) {
      if (Eta.uneta(arg._1.term()) instanceof RefTerm ref) {
        if (subst.map().containsKey(ref.var())) return null;
        subst.add(ref.var(), arg._2.toTerm());
      } else return null;
    }
    return subst;
  }

  @Nullable private Term visitCall(
    @NotNull CallTerm lhs, @NotNull CallTerm rhs,
    @NotNull DefVar<? extends Def, ? extends Decl> lhsRef
  ) {
    var retType = getType(lhs, lhsRef);
    // Lossy comparison
    var subst = levels(lhsRef, lhs.sortArgs(), rhs.sortArgs());
    if (defeq.visitArgs(lhs.args(), rhs.args(), Term.Param.subst(Def.defTele(lhsRef), subst))) return retType;
    if (defeq.compareWHNF(lhs, rhs, retType)) return retType;
    else return null;
  }

  @NotNull private LevelSubst levels(
    @NotNull DefVar<? extends Def, ? extends Decl> def,
    ImmutableSeq<@NotNull Sort> l, ImmutableSeq<@NotNull Sort> r
  ) {
    var levelSubst = new LevelSubst.Simple(MutableMap.create());
    for (var levels : l.zip(r).zip(Def.defLevels(def))) {
      defeq.levelEqns.add(levels._1._1, levels._1._2, cmp, defeq.pos);
      levelSubst.solution().put(levels._2, levels._1._1);
    }
    return levelSubst;
  }

  @NotNull private Term getType(@NotNull CallTerm lhs, @NotNull DefVar<? extends Def, ?> lhsRef) {
    var substMap = MutableMap.<Var, Term>create();
    for (var pa : lhs.args().view().zip(lhsRef.core.telescope().view())) {
      substMap.set(pa._2.ref(), pa._1.term());
    }
    return lhsRef.core.result().subst(substMap);
  }
}
