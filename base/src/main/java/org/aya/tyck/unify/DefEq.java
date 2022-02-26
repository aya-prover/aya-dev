// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.unify;

import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple2;
import org.aya.core.Meta;
import org.aya.core.def.CtorDef;
import org.aya.core.def.Def;
import org.aya.core.ops.Eta;
import org.aya.core.term.*;
import org.aya.core.visitor.Substituter;
import org.aya.core.visitor.Unfolder;
import org.aya.generic.Arg;
import org.aya.generic.util.NormalizeMode;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.ref.Var;
import org.aya.tyck.TyckState;
import org.aya.tyck.env.LocalCtx;
import org.aya.tyck.env.MapLocalCtx;
import org.aya.tyck.error.HoleProblem;
import org.aya.tyck.error.LevelError;
import org.aya.tyck.trace.Trace;
import org.aya.util.Ordering;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class DefEq {
  public record Sub(@NotNull MutableMap<@NotNull Var, @NotNull RefTerm> map) implements Cloneable {
    public Sub() {
      this(MutableMap.create());
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod") public @NotNull Sub clone() {
      return new Sub(MutableMap.from(map));
    }
  }

  public record FailureData(@NotNull Term lhs, @NotNull Term rhs) {
  }

  private final @Nullable Trace.Builder traceBuilder;
  final boolean allowVague;
  final boolean allowConfused;
  private final @NotNull TyckState state;
  private final @NotNull Reporter reporter;
  private final @NotNull SourcePos pos;
  private final @NotNull Ordering cmp;
  private final @NotNull LocalCtx ctx;
  private final @NotNull Eta uneta;
  private FailureData failure;

  public FailureData failureData() {
    return failure;
  }

  public DefEq(
    @NotNull Ordering cmp, @NotNull Reporter reporter,
    boolean allowVague, boolean allowConfused,
    @Nullable Trace.Builder traceBuilder, @NotNull TyckState state,
    @NotNull SourcePos pos, @NotNull LocalCtx ctx
  ) {
    this.cmp = cmp;
    this.allowVague = allowVague;
    this.allowConfused = allowConfused;
    this.reporter = reporter;
    this.traceBuilder = traceBuilder;
    this.state = state;
    this.pos = pos;
    this.ctx = ctx;
    uneta = new Eta(ctx);
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

  public boolean compare(@NotNull Term lhs, @NotNull Term rhs, @Nullable Term type) {
    return compare(lhs, rhs, new Sub(), new Sub(), type);
  }

  private boolean compare(Term lhs, Term rhs, Sub lr, Sub rl, @Nullable Term type) {
    if (lhs == rhs) return true;
    if (compareApprox(lhs, rhs, lr, rl) != null) return true;
    lhs = lhs.normalize(state, NormalizeMode.WHNF);
    rhs = rhs.normalize(state, NormalizeMode.WHNF);
    if (compareApprox(lhs, rhs, lr, rl) != null) return true;
    if (rhs instanceof CallTerm.Hole) return compareUntyped(rhs, lhs, rl, lr) != null;
    // ^ Beware of the order!!
    if (lhs instanceof CallTerm.Hole || type == null) return compareUntyped(lhs, rhs, lr, rl) != null;
    if (lhs instanceof ErrorTerm || rhs instanceof ErrorTerm) return true;
    var result = doCompareTyped(type.normalize(state, NormalizeMode.WHNF), lhs, rhs, lr, rl);
    if (!result && failure == null) failure = new FailureData(lhs.freezeHoles(state), rhs.freezeHoles(state));
    return result;
  }

  private @Nullable Term compareUntyped(@NotNull Term lhs, @NotNull Term rhs, Sub lr, Sub rl) {
    // lhs & rhs will both be WHNF if either is not a potentially reducible call
    if (isCall(lhs) || isCall(rhs)) {
      var ty = compareApprox(lhs, rhs, lr, rl);
      if (ty == null) ty = doCompareUntyped(lhs, rhs, lr, rl);
      if (ty != null) return ty.normalize(state, NormalizeMode.WHNF);
    }
    lhs = lhs.normalize(state, NormalizeMode.WHNF);
    rhs = rhs.normalize(state, NormalizeMode.WHNF);
    var x = doCompareUntyped(lhs, rhs, lr, rl);
    if (x != null) return x.normalize(state, NormalizeMode.WHNF);
    if (failure == null) failure = new FailureData(lhs.subst(lr.map).freezeHoles(state), rhs.freezeHoles(state));
    return null;
  }

  private boolean compareWHNF(Term lhs, Term preRhs, Sub lr, Sub rl, @NotNull Term type) {
    var whnf = lhs.normalize(state, NormalizeMode.WHNF);
    var rhsWhnf = preRhs.normalize(state, NormalizeMode.WHNF);
    if (Objects.equals(whnf, lhs) && Objects.equals(rhsWhnf, preRhs)) return false;
    return compare(whnf, rhsWhnf, lr, rl, type);
  }

  private @Nullable Term compareApprox(@NotNull Term preLhs, @NotNull Term preRhs, Sub lr, Sub rl) {
    return switch (preLhs) {
      case CallTerm.Fn lhs && preRhs instanceof CallTerm.Fn rhs ->
        lhs.ref() != rhs.ref() ? null : visitCall(lhs, rhs, lr, rl, lhs.ref());
      case CallTerm.Con lhs && preRhs instanceof CallTerm.Con rhs ->
        lhs.ref() != rhs.ref() ? null : visitCall(lhs, rhs, lr, rl, lhs.ref());
      case CallTerm.Prim lhs && preRhs instanceof CallTerm.Prim rhs ->
        lhs.ref() != rhs.ref() ? null : visitCall(lhs, rhs, lr, rl, lhs.ref());
      default -> null;
    };
  }

  private <T> T checkParam(
    Term.Param l, Term.Param r, @NotNull Term type,
    Sub lr, Sub rl, Supplier<T> fail, Supplier<T> success
  ) {
    if (l.explicit() != r.explicit()) return fail.get();
    if (!compare(l.type(), r.type(), lr, rl, type)) return fail.get();
    rl.map.put(r.ref(), l.toTerm());
    lr.map.put(l.ref(), r.toTerm());
    var result = ctx.with(l, () -> ctx.with(r, success));
    rl.map.remove(r.ref());
    lr.map.remove(l.ref());
    return result;
  }

  private <T> T checkParams(
    SeqView<Term.Param> l, SeqView<Term.Param> r,
    Sub lr, Sub rl, Supplier<T> fail, Supplier<T> success
  ) {
    if (!l.sizeEquals(r)) return fail.get();
    if (l.isEmpty()) return success.get();
    return checkParam(l.first(), r.first(), freshUniv(), lr, rl, fail, () ->
      checkParams(l.drop(1), r.drop(1), lr, rl, fail, success));
  }

  private @Contract("->new") @NotNull FormTerm.Univ freshUniv() {
    // TODO[ice]: inline?
    return FormTerm.Univ.ZERO;
  }

  private boolean visitArgs(SeqLike<Arg<Term>> l, SeqLike<Arg<Term>> r, Sub lr, Sub rl, SeqLike<Term.Param> params) {
    return visitLists(l.view().map(Arg::term), r.view().map(Arg::term), lr, rl, params);
  }

  private boolean visitLists(SeqLike<Term> l, SeqLike<Term> r, Sub lr, Sub rl, @NotNull SeqLike<Term.Param> types) {
    if (!l.sizeEquals(r)) return false;
    if (!r.sizeEquals(types)) return false;
    var typesSubst = types.view();
    var lu = l.toImmutableSeq();
    var ru = r.toImmutableSeq();
    for (int i = 0; lu.sizeGreaterThan(i); i++) {
      var li = lu.get(i);
      var head = typesSubst.first();
      if (!compare(li, ru.get(i), lr, rl, head.type())) return false;
      typesSubst = typesSubst.drop(1).map(type -> type.subst(head.ref(), li));
    }
    return true;
  }

  private @Nullable Term visitCall(
    @NotNull CallTerm lhs, @NotNull CallTerm rhs, Sub lr, Sub rl,
    @NotNull DefVar<?, ?> lhsRef
  ) {
    var retType = getType(lhs, lhsRef);
    // Lossy comparison
    if (visitArgs(lhs.args(), rhs.args(), lr, rl,
      Term.Param.subst(Def.defTele(lhsRef), lhs.ulift()))) return retType;
    if (compareWHNF(lhs, rhs, lr, rl, retType)) return retType;
    else return null;
  }

  private @NotNull Term getType(@NotNull CallTerm lhs, @NotNull DefVar<? extends Def, ?> lhsRef) {
    var substMap = MutableMap.<Var, Term>create();
    for (var pa : lhs.args().view().zip(lhsRef.core.telescope().view())) {
      substMap.set(pa._2.ref(), pa._1.term());
    }
    return lhsRef.core.result().subst(substMap);
  }

  private static boolean isCall(@NotNull Term term) {
    return term instanceof CallTerm.Fn || term instanceof CallTerm.Con || term instanceof CallTerm.Prim;
  }

  private @NotNull TyckState.Eqn createEqn(@NotNull Term lhs, @NotNull Term rhs, Sub lr, Sub rl) {
    var local = new MapLocalCtx();
    ctx.forward(local, lhs, state);
    ctx.forward(local, rhs, state);
    return new TyckState.Eqn(lhs, rhs, cmp, pos, local, lr.clone(), rl.clone());
  }

  private @Nullable Substituter.TermSubst extract(
    @NotNull CallTerm.Hole lhs, @NotNull Term rhs, @NotNull Meta meta
  ) {
    var subst = new Substituter.TermSubst(new MutableHashMap<>(/*spine.size() * 2*/));
    for (var arg : lhs.args().view().zip(meta.telescope)) {
      if (uneta.uneta(arg._1.term()) instanceof RefTerm ref) {
        if (subst.map().containsKey(ref.var())) return null;
        subst.add(ref.var(), arg._2.toTerm());
      } else return null;
    }
    return subst;
  }

  private boolean doCompareTyped(@NotNull Term type, @NotNull Term lhs, @NotNull Term rhs, Sub lr, Sub rl) {
    traceEntrance(new Trace.UnifyT(lhs.freezeHoles(state), rhs.freezeHoles(state),
      pos, type.freezeHoles(state)));
    var ret = switch (type) {
      default -> compareUntyped(lhs, rhs, lr, rl) != null;
      case CallTerm.Struct type1 -> {
        var fieldSigs = type1.ref().core.fields;
        var paramSubst = type1.ref().core.telescope().view().zip(type1.args().view()).map(x ->
          Tuple2.of(x._1.ref(), x._2.term())).<Var, Term>toImmutableMap();
        var fieldSubst = new Substituter.TermSubst(MutableHashMap.create());
        for (var fieldSig : fieldSigs) {
          var dummyVars = fieldSig.selfTele.map(par ->
            new LocalVar(par.ref().name(), par.ref().definition()));
          var dummy = dummyVars.zip(fieldSig.selfTele).map(vpa ->
            new Arg<Term>(new RefTerm(vpa._1), vpa._2.explicit()));
          var l = new CallTerm.Access(lhs, fieldSig.ref(), type1.ulift(), type1.args(), dummy);
          var r = new CallTerm.Access(rhs, fieldSig.ref(), type1.ulift(), type1.args(), dummy);
          fieldSubst.add(fieldSig.ref(), l);
          if (!compare(l, r, lr, rl, fieldSig.result().subst(paramSubst).subst(fieldSubst))) yield false;
        }
        yield true;
      }
      case IntroTerm.Lambda $ -> throw new IllegalStateException("LamTerm is never type");
      case CallTerm.Con $ -> throw new IllegalStateException("ConCall is never type");
      case IntroTerm.Tuple $ -> throw new IllegalStateException("TupTerm is never type");
      case IntroTerm.New $ -> throw new IllegalStateException("NewTerm is never type");
      case ErrorTerm $ -> true;
      case FormTerm.Sigma sigma -> {
        var params = sigma.params().view();
        for (int i = 1, size = sigma.params().size(); i <= size; i++) {
          var l = new ElimTerm.Proj(lhs, i);
          var currentParam = params.first();
          ctx.put(currentParam);
          if (!compare(l, new ElimTerm.Proj(rhs, i), lr, rl, currentParam.type())) yield false;
          params = params.drop(1).map(x -> x.subst(currentParam.ref(), l));
        }
        ctx.remove(sigma.params().view().map(Term.Param::ref));
        yield true;
      }
      case FormTerm.Pi pi -> ctx.with(pi.param(), () -> {
        if (lhs instanceof IntroTerm.Lambda lambda) return ctx.with(lambda.param(), () -> {
          if (rhs instanceof IntroTerm.Lambda rambda) return ctx.with(rambda.param(), () -> {
            lr.map.put(lambda.param().ref(), rambda.param().toTerm());
            rl.map.put(rambda.param().ref(), lambda.param().toTerm());
            return compare(lambda.body(), rambda.body(), lr, rl, pi.body());
          });
          return compare(lambda.body(), CallTerm.make(rhs, lambda.param().toArg()), lr, rl, pi.body());
        });
        if (rhs instanceof IntroTerm.Lambda rambda) return ctx.with(rambda.param(),
          () -> compare(CallTerm.make(lhs, rambda.param().toArg()), rambda.body(), lr, rl, pi.body()));
        // Question: do we need a unification for Pi.body?
        return compareUntyped(lhs, rhs, lr, rl) != null;
      });
    };
    traceExit();
    return ret;
  }

  private Term doCompareUntyped(@NotNull Term type, @NotNull Term preRhs, Sub lr, Sub rl) {
    traceEntrance(new Trace.UnifyT(type.freezeHoles(state),
      preRhs.freezeHoles(state), this.pos));
    var ret = switch (type) {
      default -> throw new IllegalStateException(type.getClass() + ": " + type);
      case RefTerm.MetaPat metaPat -> {
        var lhsRef = metaPat.ref();
        if (preRhs instanceof RefTerm.MetaPat rPat && lhsRef == rPat.ref()) yield lhsRef.type();
        else yield null;
      }
      case RefTerm lhs -> preRhs instanceof RefTerm rhs && (
        rl.map.getOrDefault(rhs.var(), rhs).var() == lhs.var() ||
          lr.map.getOrDefault(lhs.var(), lhs).var() == rhs.var()
      ) ? ctx.get(lhs.var()) : null;
      case ElimTerm.App lhs -> {
        if (!(preRhs instanceof ElimTerm.App rhs)) yield null;
        var preFnType = compareUntyped(lhs.of(), rhs.of(), lr, rl);
        if (!(preFnType instanceof FormTerm.Pi fnType)) yield null;
        if (!compare(lhs.arg().term(), rhs.arg().term(), lr, rl, fnType.param().type())) yield null;
        yield fnType.substBody(lhs.arg().term());
      }
      case ElimTerm.Proj lhs -> {
        if (!(preRhs instanceof ElimTerm.Proj rhs)) yield null;
        var preTupType = compareUntyped(lhs.of(), rhs.of(), lr, rl);
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
        yield checkParam(lhs.param(), rhs.param(), freshUniv(), lr, rl, () -> null, () -> {
          var bodyIsOk = compare(lhs.body(), rhs.body(), lr, rl, freshUniv());
          if (!bodyIsOk) return null;
          return freshUniv();
        });
      }
      case FormTerm.Sigma lhs -> {
        if (!(preRhs instanceof FormTerm.Sigma rhs)) yield null;
        yield checkParams(lhs.params().view(), rhs.params().view(), lr, rl, () -> null, this::freshUniv);
      }
      case FormTerm.Univ lhs -> {
        if (!(preRhs instanceof FormTerm.Univ rhs)) yield null;
        compareLevel(lhs.lift(), rhs.lift());
        yield new FormTerm.Univ((cmp == Ordering.Lt ? lhs : rhs).lift() + 1);
      }
      // See compareApprox for why we don't compare these
      case CallTerm.Fn lhs -> null;
      case CallTerm.Data lhs -> {
        if (!(preRhs instanceof CallTerm.Data rhs) || lhs.ref() != rhs.ref()) yield null;
        var args = visitArgs(lhs.args(), rhs.args(), lr, rl, Term.Param.subst(Def.defTele(lhs.ref()), lhs.ulift()));
        // Do not need to be computed precisely because unification won't need this info
        yield args ? freshUniv() : null;
      }
      case CallTerm.Struct lhs -> {
        if (!(preRhs instanceof CallTerm.Struct rhs) || lhs.ref() != rhs.ref()) yield null;
        var args = visitArgs(lhs.args(), rhs.args(), lr, rl, Term.Param.subst(Def.defTele(lhs.ref()), lhs.ulift()));
        yield args ? freshUniv() : null;
      }
      case CallTerm.Con lhs -> {
        if (!(preRhs instanceof CallTerm.Con rhs) || lhs.ref() != rhs.ref()) yield null;
        var retType = getType(lhs, lhs.ref());
        // Lossy comparison
        if (visitArgs(lhs.conArgs(), rhs.conArgs(), lr, rl, Term.Param.subst(CtorDef.conTele(lhs.ref()), lhs.ulift())))
          yield retType;
        yield null;
      }
      case CallTerm.Prim lhs -> null;
      case CallTerm.Access lhs -> {
        if (!(preRhs instanceof CallTerm.Access rhs)) yield null;
        var preStructType = compareUntyped(lhs.of(), rhs.of(), lr, rl);
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
            if (!compare(arg._1.term(), arg._2.term(), lr, rl, holePi.param().type())) yield null;
            holeTy = holePi.substBody(arg._1.term());
          }
          yield holeTy;
        }
        // Long time ago I wrote this to generate more unification equations,
        // which solves more universe levels. However, with latest version Aya (0.13),
        // removing this does not break anything.
        // compareUntyped(preRhs.computeType(state, ctx), meta.result);
        var argSubst = extract(lhs, preRhs, meta);
        if (argSubst == null) {
          reporter.report(new HoleProblem.BadSpineError(lhs, pos));
          yield null;
        }
        var subst = Unfolder.buildSubst(meta.contextTele, lhs.contextArgs());
        // In this case, the solution may not be unique (see #608),
        // so we may delay its resolution to the end of the tycking when we disallow vague unification.
        if (!allowVague && subst.overlap(argSubst).anyMatch(var -> preRhs.findUsages(var) > 0)) {
          state.addEqn(createEqn(lhs, preRhs, lr, rl));
          // Skip the unification and scope check
          yield meta.result;
        }
        subst.add(argSubst);
        // TODO
        rl.map.forEach(subst::add);
        assert !state.metas().containsKey(meta);
        var solved = preRhs.freezeHoles(state).subst(subst);
        var allowedVars = meta.fullTelescope().map(Term.Param::ref).toImmutableSeq();
        var scopeCheck = solved.scopeCheck(allowedVars);
        if (scopeCheck.invalid.isNotEmpty()) {
          // Normalization may remove the usages of certain variables
          solved = solved.normalize(state, NormalizeMode.NF);
          scopeCheck = solved.scopeCheck(allowedVars);
        }
        if (scopeCheck.invalid.isNotEmpty()) {
          reporter.report(new HoleProblem.BadlyScopedError(lhs, solved, scopeCheck.invalid, pos));
          yield new ErrorTerm(solved);
        }
        if (scopeCheck.confused.isNotEmpty()) {
          if (allowConfused) state.addEqn(createEqn(lhs, solved, lr, rl));
          else {
            reporter.report(new HoleProblem.BadlyScopedError(lhs, solved, scopeCheck.confused, pos));
            yield new ErrorTerm(solved);
          }
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

  private void compareLevel(int l, int r) {
    switch (cmp) {
      case Eq:
        if (l != r) reporter.report(new LevelError(pos, l, r, true));
      case Gt:
        if (l < r) reporter.report(new LevelError(pos, l, r, false));
      case Lt:
        if (l > r) reporter.report(new LevelError(pos, r, l, false));
    }
  }

  public void checkEqn(@NotNull TyckState.Eqn eqn) {
    compareUntyped(
      eqn.lhs().normalize(state, NormalizeMode.WHNF),
      eqn.rhs().normalize(state, NormalizeMode.WHNF),
      eqn.lr(), eqn.rl()
    );
  }
}
