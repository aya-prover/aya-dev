// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.unify;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple2;
import org.aya.api.error.Reporter;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.aya.api.util.Arg;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.stmt.Signatured;
import org.aya.core.Meta;
import org.aya.core.def.CtorDef;
import org.aya.core.def.Def;
import org.aya.core.ops.Eta;
import org.aya.core.sort.LevelSubst;
import org.aya.core.sort.Sort;
import org.aya.core.term.*;
import org.aya.core.visitor.Substituter;
import org.aya.core.visitor.Unfolder;
import org.aya.tyck.LocalCtx;
import org.aya.tyck.TyckState;
import org.aya.tyck.error.HoleProblem;
import org.aya.tyck.trace.Trace;
import org.aya.util.Ordering;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class DefEq {
  private final @NotNull MutableMap<@NotNull LocalVar, @NotNull RefTerm> varSubst = new MutableHashMap<>();
  private final @Nullable Trace.Builder traceBuilder;
  final boolean allowVague;
  private final @NotNull TyckState state;
  private final @NotNull Reporter reporter;
  private final @NotNull SourcePos pos;
  private final @NotNull Ordering cmp;
  private final @NotNull LocalCtx ctx;

  public DefEq(
    @NotNull Ordering cmp, @NotNull Reporter reporter, boolean allowVague,
    @Nullable Trace.Builder traceBuilder, @NotNull TyckState state,
    @NotNull SourcePos pos, @NotNull LocalCtx ctx
  ) {
    this.cmp = cmp;
    this.allowVague = allowVague;
    this.reporter = reporter;
    this.traceBuilder = traceBuilder;
    this.state = state;
    this.pos = pos;
    this.ctx = ctx;
  }

  private void tracing(@NotNull Consumer<Trace.@NotNull Builder> consumer) {
    if (traceBuilder != null) consumer.accept(traceBuilder);
  }

  private void traceEntrance(@NotNull Trace trace) {
    tracing(builder -> builder.shift(trace));
  }

  private void traceExit() {
    tracing(Trace.Builder::reduce);
  }

  public boolean compare(@NotNull Term lhs, @NotNull Term rhs, @NotNull Term type) {
    if (lhs == rhs) return true;
    if (compareApprox(lhs, rhs) != null) return true;
    lhs = lhs.normalize(state, NormalizeMode.WHNF);
    rhs = rhs.normalize(state, NormalizeMode.WHNF);
    if (compareApprox(lhs, rhs) != null) return true;
    if (rhs instanceof CallTerm.Hole) return compareUntyped(rhs, lhs) != null;
    if (lhs instanceof CallTerm.Hole) return compareUntyped(lhs, rhs) != null;
    if (lhs instanceof ErrorTerm || rhs instanceof ErrorTerm) return true;
    return doCompareTyped(type.normalize(state, NormalizeMode.WHNF), lhs, rhs);
  }

  public @Nullable Term compareUntyped(@NotNull Term lhs, @NotNull Term rhs) {
    // lhs & rhs will both be WHNF if either is not a potentially reducible call
    if (isCall(lhs) || isCall(rhs)) {
      var ty = compareApprox(lhs, rhs);
      if (ty == null) ty = doCompareUntyped(lhs, rhs);
      if (ty != null) return ty.normalize(state, NormalizeMode.WHNF);
    }
    lhs = lhs.normalize(state, NormalizeMode.WHNF);
    rhs = rhs.normalize(state, NormalizeMode.WHNF);
    final var x = doCompareUntyped(lhs, rhs);
    return x != null ? x.normalize(state, NormalizeMode.WHNF) : null;
  }

  private boolean compareWHNF(Term lhs, Term preRhs, @NotNull Term type) {
    var whnf = lhs.normalize(state, NormalizeMode.WHNF);
    var rhsWhnf = preRhs.normalize(state, NormalizeMode.WHNF);
    if (Objects.equals(whnf, lhs) && Objects.equals(rhsWhnf, preRhs)) return false;
    return compare(whnf, rhsWhnf, type);
  }

  private @Nullable Term compareApprox(@NotNull Term preLhs, @NotNull Term preRhs) {
    return switch (preLhs) {
      case CallTerm.Fn lhs && preRhs instanceof CallTerm.Fn rhs -> lhs.ref() != rhs.ref() ? null : visitCall(lhs, rhs, lhs.ref());
      case CallTerm.Con lhs && preRhs instanceof CallTerm.Con rhs -> lhs.ref() != rhs.ref() ? null : visitCall(lhs, rhs, lhs.ref());
      case CallTerm.Prim lhs && preRhs instanceof CallTerm.Prim rhs -> lhs.ref() != rhs.ref() ? null : visitCall(lhs, rhs, lhs.ref());
      default -> null;
    };
  }

  private <T> T checkParam(Term.@NotNull Param l, Term.@NotNull Param r, @NotNull Term type, Supplier<T> fail, Supplier<T> success) {
    if (l.explicit() != r.explicit()) return fail.get();
    if (!compare(l.type(), r.type(), type)) return fail.get();
    varSubst.put(r.ref(), l.toTerm());
    varSubst.put(l.ref(), r.toTerm());
    var result = success.get();
    varSubst.remove(r.ref());
    varSubst.remove(l.ref());
    return result;
  }

  private <T> T checkParams(SeqLike<Term.@NotNull Param> l, SeqLike<Term.@NotNull Param> r, Supplier<T> fail, Supplier<T> success) {
    if (!l.sizeEquals(r)) return fail.get();
    if (l.isEmpty()) return success.get();
    return checkParam(l.first(), r.first(), freshUniv(), fail, () ->
      checkParams(l.view().drop(1), r.view().drop(1), fail, success));
  }

  private @Contract("->new") @NotNull FormTerm.Univ freshUniv() {
    // [ice]: the generated univ var may not be used since in most cases we just return `freshUniv()`
    // and we test if it's non-null. So the level variable won't even present in the level equations.
    return FormTerm.freshUniv(pos);
  }

  private boolean visitArgs(SeqLike<Arg<Term>> l, SeqLike<Arg<Term>> r, SeqLike<Term.Param> params) {
    return visitLists(l.view().map(Arg::term), r.view().map(Arg::term), params);
  }

  private boolean visitLists(SeqLike<Term> l, SeqLike<Term> r, @NotNull SeqLike<Term.Param> types) {
    if (!l.sizeEquals(r)) return false;
    if (!r.sizeEquals(types)) return false;
    var typesSubst = types.view();
    var lu = l.toImmutableSeq();
    var ru = r.toImmutableSeq();
    for (int i = 0; lu.sizeGreaterThan(i); i++) {
      var li = lu.get(i);
      var head = typesSubst.first();
      if (!compare(li, ru.get(i), head.type())) return false;
      typesSubst = typesSubst.drop(1).map(type -> type.subst(head.ref(), li));
    }
    return true;
  }

  @Nullable private Term visitCall(
    @NotNull CallTerm lhs, @NotNull CallTerm rhs,
    @NotNull DefVar<? extends Def, ? extends Signatured> lhsRef
  ) {
    var retType = getType(lhs, lhsRef);
    // Lossy comparison
    var subst = levels(lhsRef, lhs.sortArgs(), rhs.sortArgs());
    if (visitArgs(lhs.args(), rhs.args(), Term.Param.subst(Def.defTele(lhsRef), subst))) return retType;
    if (compareWHNF(lhs, rhs, retType)) return retType;
    else return null;
  }

  @NotNull private LevelSubst levels(
    @NotNull DefVar<? extends Def, ? extends Signatured> def,
    ImmutableSeq<@NotNull Sort> l, ImmutableSeq<@NotNull Sort> r
  ) {
    var levelSubst = new LevelSubst.Simple(MutableMap.create());
    for (var levels : l.zip(r).zip(Def.defLevels(def))) {
      state.levelEqns().add(levels._1._1, levels._1._2, this.cmp, this.pos);
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

  public static boolean isCall(@NotNull Term term) {
    return term instanceof CallTerm.Fn || term instanceof CallTerm.Con || term instanceof CallTerm.Prim;
  }

  private @NotNull TyckState.Eqn createEqn(@NotNull Term lhs, @NotNull Term rhs) {
    var local = new LocalCtx();
    ctx.forward(local, lhs, state);
    ctx.forward(local, rhs, state);
    return new TyckState.Eqn(lhs, rhs, cmp, pos, local, varSubst.toImmutableMap());
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

  private boolean doCompareTyped(@NotNull Term type, @NotNull Term lhs, @NotNull Term rhs) {
    traceEntrance(new Trace.UnifyT(lhs.freezeHoles(state), rhs.freezeHoles(state),
      pos, type.freezeHoles(state)));
    var ret = switch (type) {
      default -> compareUntyped(lhs, rhs) != null;
      case CallTerm.Struct type1 -> {
        var fieldSigs = type1.ref().core.fields;
        var paramSubst = type1.ref().core.telescope().view().zip(type1.args().view()).map(x ->
          Tuple2.of(x._1.ref(), x._2.term())).<Var, Term>toImmutableMap();
        var fieldSubst = new Substituter.TermSubst(MutableHashMap.create());
        for (var fieldSig : fieldSigs) {
          var dummyVars = fieldSig.selfTele.map(par ->
            new LocalVar(par.ref().name(), par.ref().definition()));
          var dummy = dummyVars.zip(fieldSig.selfTele).map(vpa ->
            new Arg<Term>(new RefTerm(vpa._1, vpa._2.type()), vpa._2.explicit()));
          var l = new CallTerm.Access(lhs, fieldSig.ref(), type1.sortArgs(), type1.args(), dummy);
          var r = new CallTerm.Access(rhs, fieldSig.ref(), type1.sortArgs(), type1.args(), dummy);
          fieldSubst.add(fieldSig.ref(), l);
          if (!compare(l, r, fieldSig.result().subst(paramSubst).subst(fieldSubst))) yield false;
        }
        yield true;
      }
      case IntroTerm.Lambda $ -> throw new IllegalStateException("LamTerm is never type");
      case CallTerm.Con $ -> throw new IllegalStateException("ConCall is never type");
      case IntroTerm.Tuple $ -> throw new IllegalStateException("TupTerm is never type");
      case IntroTerm.New $ -> throw new IllegalStateException("NewTerm is never type");
      case ErrorTerm $ -> true;
      case FormTerm.Sigma type1 -> {
        var params = type1.params().view();
        for (int i = 1, size = type1.params().size(); i <= size; i++) {
          var l = new ElimTerm.Proj(lhs, i);
          var currentParam = params.first();
          if (!compare(l, new ElimTerm.Proj(rhs, i), currentParam.type())) yield false;
          params = params.drop(1).map(x -> x.subst(currentParam.ref(), l));
        }
        yield true;
      }
      case FormTerm.Pi type1 -> {
        var dummyVar = new LocalVar("dummy");
        var ty = type1.param().type();
        var dummy = new RefTerm(dummyVar, ty);
        var dummyArg = new Arg<Term>(dummy, type1.param().explicit());
        yield compare(CallTerm.make(lhs, dummyArg), CallTerm.make(rhs, dummyArg), type1.substBody(dummy));
      }
    };
    traceExit();
    return ret;
  }

  private Term doCompareUntyped(@NotNull Term type, @NotNull Term preRhs) {
    traceEntrance(new Trace.UnifyT(type.freezeHoles(state),
      preRhs.freezeHoles(state), this.pos));
    var ret = switch (type) {
      default -> throw new IllegalStateException();
      case RefTerm.MetaPat metaPat -> {
        var lhsRef = metaPat.ref();
        if (preRhs instanceof RefTerm.MetaPat rPat && lhsRef == rPat.ref()) yield lhsRef.type();
        else yield null;
      }
      case RefTerm lhs -> {
        if (preRhs instanceof RefTerm rhs
          && varSubst.getOrDefault(rhs.var(), rhs).var() == lhs.var()) {
          yield rhs.type();
        }
        yield null;
      }
      case ElimTerm.App lhs -> {
        if (!(preRhs instanceof ElimTerm.App rhs)) yield null;
        var preFnType = compareUntyped(lhs.of(), rhs.of());
        if (!(preFnType instanceof FormTerm.Pi fnType)) yield null;
        if (!compare(lhs.arg().term(), rhs.arg().term(), fnType.param().type())) yield null;
        yield fnType.substBody(lhs.arg().term());
      }
      case ElimTerm.Proj lhs -> {
        if (!(preRhs instanceof ElimTerm.Proj rhs)) yield null;
        var preTupType = compareUntyped(lhs.of(), rhs.of());
        if (!(preTupType instanceof FormTerm.Sigma tupType)) yield null;
        if (lhs.ix() != rhs.ix()) yield null;
        var params = tupType.params().view();
        var subst = new Substituter.TermSubst(MutableMap.create());
        for (int i = 1; i < lhs.ix(); i++) {
          var l = new ElimTerm.Proj(lhs, i);
          var currentParam = params.first();
          subst.add(currentParam.ref(), l);
          params = params.drop(1);
        }
        if (params.isNotEmpty()) yield params.first().subst(subst).type();
        yield params.last().subst(subst).type();
      }
      case ErrorTerm term -> ErrorTerm.typeOf(term.freezeHoles(state));
      case FormTerm.Pi lhs -> {
        if (!(preRhs instanceof FormTerm.Pi rhs)) yield null;
        yield checkParam(lhs.param(), rhs.param(), freshUniv(), () -> null, () -> {
          var bodyIsOk = compare(lhs.body(), rhs.body(), freshUniv());
          if (!bodyIsOk) return null;
          return freshUniv();
        });
      }
      case FormTerm.Sigma lhs -> {
        if (!(preRhs instanceof FormTerm.Sigma rhs)) yield null;
        yield checkParams(lhs.params(), rhs.params(), () -> null, () -> {
          var bodyIsOk = compare(lhs.params().last().type(), rhs.params().last().type(), freshUniv());
          if (!bodyIsOk) return null;
          return freshUniv();
        });
      }
      case FormTerm.Univ lhs -> {
        if (!(preRhs instanceof FormTerm.Univ rhs)) yield null;
        state.levelEqns().add(lhs.sort(), rhs.sort(), cmp, this.pos);
        yield new FormTerm.Univ((cmp == Ordering.Lt ? lhs : rhs).sort().lift(1));
      }
      // See compareApprox for why we don't compare these
      case CallTerm.Fn lhs -> null;
      case CallTerm.Data lhs -> {
        if (!(preRhs instanceof CallTerm.Data rhs) || lhs.ref() != rhs.ref()) yield null;
        var subst = levels(lhs.ref(), lhs.sortArgs(), rhs.sortArgs());
        var args = visitArgs(lhs.args(), rhs.args(), Term.Param.subst(Def.defTele(lhs.ref()), subst));
        // Do not need to be computed precisely because unification won't need this info
        yield args ? freshUniv() : null;
      }
      case CallTerm.Struct lhs -> {
        if (!(preRhs instanceof CallTerm.Struct rhs) || lhs.ref() != rhs.ref()) yield null;
        var subst = levels(lhs.ref(), lhs.sortArgs(), rhs.sortArgs());
        var args = visitArgs(lhs.args(), rhs.args(), Term.Param.subst(Def.defTele(lhs.ref()), subst));
        yield args ? freshUniv() : null;
      }
      case CallTerm.Con lhs -> {
        if (!(preRhs instanceof CallTerm.Con rhs) || lhs.ref() != rhs.ref()) yield null;
        var retType = getType(lhs, lhs.ref());
        // Lossy comparison
        var subst = levels(lhs.head().dataRef(), lhs.sortArgs(), rhs.sortArgs());
        if (visitArgs(lhs.conArgs(), rhs.conArgs(), Term.Param.subst(CtorDef.conTele(lhs.ref()), subst)))
          yield retType;
        yield null;
      }
      case CallTerm.Prim lhs -> null;
      case CallTerm.Access lhs -> {
        if (!(preRhs instanceof CallTerm.Access rhs)) yield null;
        var preStructType = compareUntyped(lhs.of(), rhs.of());
        if (!(preStructType instanceof CallTerm.Struct structType)) yield null;
        if (lhs.ref() != rhs.ref()) yield null;
        yield Def.defResult(lhs.ref());
      }
      case CallTerm.Hole lhs -> {
        var meta = lhs.ref();
        if (preRhs instanceof CallTerm.Hole rcall && lhs.ref() == rcall.ref()) {
          var holeTy = FormTerm.Pi.make(meta.telescope, meta.result);
          for (var arg : lhs.args().view().zip(rcall.args())) {
            if (!(holeTy instanceof FormTerm.Pi holePi))
              throw new IllegalStateException("meta arg size larger than param size. this should not happen");
            if (!compare(arg._1.term(), arg._2.term(), holePi.param().type())) yield null;
            holeTy = holePi.substBody(arg._1.term());
          }
          yield holeTy;
        }
        // Long time ago I wrote this to offer more unification equations,
        // which solves more universe levels. However, with latest version Aya (0.13),
        // removing this does not break anything.
        // compareUntyped(preRhs.accept(new LittleTyper(state, ctx), Unit.unit()), meta.result);
        var argSubst = extract(lhs, preRhs, meta);
        if (argSubst == null) {
          reporter.report(new HoleProblem.BadSpineError(lhs, pos));
          yield null;
        }
        var subst = Unfolder.buildSubst(meta.contextTele, lhs.contextArgs());
        // In this case, the solution may not be unique (see #608),
        // so we may delay its resolution to the end of the tycking when we disallow vague unification.
        if (!allowVague && subst.overlap(argSubst).anyMatch(var -> preRhs.findUsages(var) > 0)) {
          state.addEqn(createEqn(lhs, preRhs));
          // Skip the unification and scope check
          yield meta.result;
        }
        subst.add(argSubst);
        varSubst.forEach(subst::add);
        assert !state.metas().containsKey(meta);
        var solved = preRhs.subst(subst).freezeHoles(state);
        var scopeCheck = solved.scopeCheck(meta.fullTelescope().map(Term.Param::ref).toImmutableSeq());
        if (scopeCheck.isNotEmpty()) {
          reporter.report(new HoleProblem.BadlyScopedError(lhs, solved, scopeCheck, pos));
          yield new ErrorTerm(solved);
        }
        if (!meta.solve(state, solved)) {
          reporter.report(new HoleProblem.RecursionError(lhs, solved, pos));
          yield new ErrorTerm(solved);
        }
        tracing(builder -> builder.append(new Trace.LabelT(pos, "Hole solved!")));
        yield meta.result;
      }
    };
    traceExit();
    return ret;
  }

  public void checkEqn(@NotNull TyckState.Eqn eqn) {
    varSubst.putAll(eqn.varSubst());
    compareUntyped(eqn.lhs().normalize(state, NormalizeMode.WHNF), eqn.rhs().normalize(state, NormalizeMode.WHNF));
  }
}
