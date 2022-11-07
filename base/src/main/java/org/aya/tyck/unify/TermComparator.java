// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.unify;

import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import org.aya.concrete.stmt.Decl;
import org.aya.core.def.CtorDef;
import org.aya.core.def.Def;
import org.aya.core.def.PrimDef;
import org.aya.core.term.*;
import org.aya.core.visitor.Subst;
import org.aya.generic.Arg;
import org.aya.generic.SortKind;
import org.aya.generic.util.InternalException;
import org.aya.generic.util.NormalizeMode;
import org.aya.guest0x0.cubical.CofThy;
import org.aya.guest0x0.cubical.Partial;
import org.aya.guest0x0.cubical.Restr;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.tyck.TyckState;
import org.aya.tyck.env.LocalCtx;
import org.aya.tyck.error.LevelError;
import org.aya.tyck.trace.Trace;
import org.aya.util.Ordering;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Bidirectional unification of terms, with abstract {@link TermComparator#solveMeta}.
 * This class is not called <code>Comparator</code> because there is already {@link java.util.Comparator}.
 *
 * @see Unifier Pattern unification implementation
 * @see TermComparator#compareUntyped(Term, Term, Sub, Sub) the "synthesize" direction
 * @see TermComparator#compare(Term, Term, Sub, Sub, Term) the "inherit" direction
 */
public sealed abstract class TermComparator permits Unifier {
  protected final @Nullable Trace.Builder traceBuilder;
  protected final @NotNull TyckState state;
  protected final @NotNull Reporter reporter;
  protected final @NotNull SourcePos pos;
  protected final @NotNull Ordering cmp;
  protected final @NotNull LocalCtx ctx;
  private FailureData failure;

  public TermComparator(@Nullable Trace.Builder traceBuilder, @NotNull TyckState state, @NotNull Reporter reporter, @NotNull SourcePos pos, @NotNull Ordering cmp, @NotNull LocalCtx ctx) {
    this.traceBuilder = traceBuilder;
    this.state = state;
    this.reporter = reporter;
    this.pos = pos;
    this.cmp = cmp;
    this.ctx = ctx;
  }

  private static boolean isCall(@NotNull Term term) {
    return term instanceof CallTerm.Fn || term instanceof CallTerm.Con || term instanceof CallTerm.Prim;
  }

  public static <E> E withIntervals(
    @NotNull ImmutableSeq<LocalVar> l, @NotNull ImmutableSeq<LocalVar> r, Sub lr, Sub rl,
    @NotNull ImmutableSeq<LocalVar> tyVars,
    @NotNull BiFunction<Subst, Subst, E> supplier
  ) {
    assert l.sizeEquals(r);
    var lSubst = new Subst();
    var rSubst = new Subst();
    for (var conv : l.view().zip3(r, tyVars)) {
      lr.map.put(conv._3, new RefTerm(conv._2));
      rl.map.put(conv._3, new RefTerm(conv._1));
      lSubst.addDirectly(conv._1, new RefTerm(conv._3));
      rSubst.addDirectly(conv._2, new RefTerm(conv._3));
    }
    var res = supplier.apply(lSubst, rSubst);
    lr.map.removeAll(tyVars);
    rl.map.removeAll(tyVars);
    return res;
  }

  private static boolean sortLt(FormTerm.Sort l, FormTerm.Sort r) {
    var lift = l.lift();
    var rift = r.lift();
    return switch (l.kind()) {
      case Type -> switch (r.kind()) {
        case Type -> lift <= rift;
        case Set -> lift <= rift;
        case default -> false;
      };
      case ISet -> switch (r.kind()) {
        case ISet -> true;
        case Set -> true;
        case default -> false;
      };
      case Prop -> r.kind() == SortKind.Prop;
      case Set -> r.kind() == SortKind.Set && lift <= rift;
    };
  }

  public @NotNull FailureData getFailure() {
    assert failure != null;
    return failure;
  }

  protected final void tracing(@NotNull Consumer<Trace.@NotNull Builder> consumer) {
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

  protected final boolean compare(Term lhs, Term rhs, Sub lr, Sub rl, @Nullable Term type) {
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

  @Nullable
  protected Term compareUntyped(@NotNull Term lhs, @NotNull Term rhs, Sub lr, Sub rl) {
    // lhs & rhs will both be WHNF if either is not a potentially reducible call
    if (TermComparator.isCall(lhs) || TermComparator.isCall(rhs)) {
      var ty = compareApprox(lhs, rhs, lr, rl);
      if (ty == null) ty = doCompareUntyped(lhs, rhs, lr, rl);
      if (ty != null) return ty.normalize(state, NormalizeMode.WHNF);
    }
    lhs = lhs.normalize(state, NormalizeMode.WHNF);
    rhs = rhs.normalize(state, NormalizeMode.WHNF);
    var x = doCompareUntyped(lhs, rhs, lr, rl);
    if (x != null) return x.normalize(state, NormalizeMode.WHNF);
    if (failure == null) failure = new FailureData(lhs.freezeHoles(state), rhs.freezeHoles(state));
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
      case CallTerm.Fn lhs when preRhs instanceof CallTerm.Fn rhs ->
        lhs.ref() != rhs.ref() ? null : visitCall(lhs, rhs, lr, rl, lhs.ref(), lhs.ulift());
      case CallTerm.Con lhs when preRhs instanceof CallTerm.Con rhs ->
        lhs.ref() != rhs.ref() ? null : visitCall(lhs, rhs, lr, rl, lhs.ref(), lhs.ulift());
      case CallTerm.Prim lhs when preRhs instanceof CallTerm.Prim rhs ->
        lhs.ref() != rhs.ref() ? null : visitCall(lhs, rhs, lr, rl, lhs.ref(), lhs.ulift());
      default -> null;
    };
  }

  private <T> T checkParam(
    Term.Param l, Term.Param r, @Nullable Term type, Subst lsub, Subst rsub,
    Sub lr, Sub rl, Supplier<T> fail, BiFunction<Subst, Subst, T> success
  ) {
    if (l.explicit() != r.explicit()) return fail.get();
    var lTy = l.type().subst(lsub);
    if (!compare(lTy, r.type().subst(rsub), lr, rl, type)) return fail.get();
    // Do not substitute when one side is ignored
    if (l.ref() == LocalVar.IGNORED || r.ref() == LocalVar.IGNORED) {
      return success.apply(lsub, rsub);
    } else {
      var i = new LocalVar(l.ref().name() + r.ref().name());
      rl.map.put(i, l.toTerm());
      lr.map.put(i, r.toTerm());
      var term = new RefTerm(i);
      lsub.addDirectly(l.ref(), term);
      rsub.addDirectly(r.ref(), term);
      var result = ctx.with(i, lTy, () -> success.apply(lsub, rsub));
      rl.map.remove(i);
      lr.map.remove(i);
      return result;
    }
  }

  private <T> T checkParams(
    SeqView<Term.Param> l, SeqView<Term.Param> r, Subst lsub, Subst rsub,
    Sub lr, Sub rl, Supplier<T> fail, BiFunction<Subst, Subst, T> success
  ) {
    if (!l.sizeEquals(r)) return fail.get();
    if (l.isEmpty()) return success.apply(lsub, rsub);
    return checkParam(l.first(), r.first(), null, lsub, rsub, lr, rl, fail, (ls, rs) ->
      checkParams(l.drop(1), r.drop(1), ls, rs, lr, rl, fail, success));
  }

  private <T> T checkParams(
    SeqView<Term.Param> l, SeqView<Term.Param> r,
    Sub lr, Sub rl, Supplier<T> fail, BiFunction<Subst, Subst, T> success
  ) {
    return checkParams(l, r, new Subst(), new Subst(), lr, rl, fail, success);
  }

  private boolean visitArgs(SeqLike<Arg<Term>> l, SeqLike<Arg<Term>> r, Sub lr, Sub rl, SeqLike<Term.Param> params) {
    return visitLists(l.view().map(Arg::term), r.view().map(Arg::term), lr, rl, params);
  }

  private boolean visitLists(SeqView<Term> l, SeqView<Term> r, Sub lr, Sub rl, @NotNull SeqLike<Term.Param> types) {
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
    @NotNull DefVar<? extends Def, ? extends Decl.Telescopic> lhsRef, int ulift
  ) {
    var retType = getType(lhs, lhsRef);
    // Lossy comparison
    if (visitArgs(lhs.args(), rhs.args(), lr, rl,
      Term.Param.subst(Def.defTele(lhsRef), ulift))) return retType;
    if (compareWHNF(lhs, rhs, lr, rl, retType)) return retType;
    else return null;
  }

  private record Pair(Term lhs, Term rhs) {}

  private @NotNull Term getType(@NotNull CallTerm lhs, @NotNull DefVar<? extends Def, ?> lhsRef) {
    var substMap = MutableMap.<AnyVar, Term>create();
    for (var pa : lhs.args().view().zip(lhsRef.core.telescope().view())) {
      substMap.set(pa._2.ref(), pa._1.term());
    }
    return lhsRef.core.result().subst(substMap);
  }

  private boolean doCompareTyped(@NotNull Term type, @NotNull Term lhs, @NotNull Term rhs, Sub lr, Sub rl) {
    traceEntrance(new Trace.UnifyT(lhs.freezeHoles(state), rhs.freezeHoles(state),
      pos, type.freezeHoles(state)));
    var ret = switch (type) {
      default -> compareUntyped(lhs, rhs, lr, rl) != null;
      case CallTerm.Struct type1 -> {
        var fieldSigs = type1.ref().core.fields;
        var paramSubst = type1.ref().core.telescope().view().zip(type1.args().view()).map(x ->
          Tuple.of(x._1.ref(), x._2.term())).<AnyVar, Term>toImmutableMap();
        var fieldSubst = new Subst(MutableHashMap.create());
        for (var fieldSig : fieldSigs) {
          var dummyVars = fieldSig.selfTele.map(par ->
            new LocalVar(par.ref().name(), par.ref().definition()));
          var dummy = dummyVars.zip(fieldSig.selfTele).map(vpa ->
            new Arg<Term>(new RefTerm(vpa._1), vpa._2.explicit()));
          var l = new CallTerm.Access(lhs, fieldSig.ref(), type1.args(), dummy);
          var r = new CallTerm.Access(rhs, fieldSig.ref(), type1.args(), dummy);
          fieldSubst.add(fieldSig.ref(), l);
          if (!compare(l, r, lr, rl, fieldSig.result().subst(paramSubst).subst(fieldSubst))) yield false;
        }
        yield true;
      }
      case IntroTerm.Lambda $ -> throw new InternalException("LamTerm is never type");
      case CallTerm.Con $ -> throw new InternalException("ConCall is never type");
      case IntroTerm.Tuple $ -> throw new InternalException("TupTerm is never type");
      case IntroTerm.New $ -> throw new InternalException("NewTerm is never type");
      case ErrorTerm $ -> true;
      case FormTerm.Sigma(var paramsSeq) -> {
        var params = paramsSeq.view();
        for (int i = 1, size = paramsSeq.size(); i <= size; i++) {
          var l = ElimTerm.proj(lhs, i);
          var currentParam = params.first();
          ctx.put(currentParam);
          if (!compare(l, ElimTerm.proj(rhs, i), lr, rl, currentParam.type())) yield false;
          params = params.drop(1).map(x -> x.subst(currentParam.ref(), l));
        }
        ctx.remove(paramsSeq.view().map(Term.Param::ref));
        yield true;
      }
      case FormTerm.Pi pi -> ctx.with(pi.param(), () -> switch (new Pair(lhs, rhs)) {
        case Pair(IntroTerm.Lambda(var lp,var lb),IntroTerm.Lambda(var rp,var rb)) -> {
          var ref = pi.param().ref();
          if (ref == LocalVar.IGNORED) ref = new LocalVar(lp.ref().name() + rp.ref().name());
          lr.map.put(ref, rp.toTerm());
          rl.map.put(ref, lp.toTerm());
          var piParam = new RefTerm(ref);
          var res = compare(lb.subst(lp.ref(), piParam), rb.subst(rp.ref(), piParam), lr, rl, pi.body());
          lr.map.remove(ref);
          rl.map.remove(ref);
          yield res;
        }
        case Pair(var $,IntroTerm.Lambda rambda) -> compareLambdaBody(rambda, lhs, rl, lr, pi);
        case Pair(IntroTerm.Lambda lambda,var $) -> compareLambdaBody(lambda, rhs, lr, rl, pi);
        // Question: do we need a unification for Pi.body?
        case default -> compare(lhs, rhs, lr, rl, null);
      });
      // In this case, both sides have the same type (I hope)
      case FormTerm.Path(var cube) -> ctx.withIntervals(cube.params().view(), () -> {
        if (lhs instanceof IntroTerm.PathLam lambda) {
          assert lambda.params().sizeEquals(cube.params());
          if (rhs instanceof IntroTerm.PathLam(var rparams,var rbody)) {
            assert rparams.sizeEquals(cube.params());
            withIntervals(lambda.params(), rparams, lr, rl, cube.params(), (lsub, rsub) ->
              compare(lambda.body().subst(lsub), rbody.subst(rsub), lr, rl, cube.type()));
          }
          return comparePathLamBody(lambda, rhs, lr, rl, cube);
        }
        if (rhs instanceof IntroTerm.PathLam rambda) {
          assert rambda.params().sizeEquals(cube.params());
          return comparePathLamBody(rambda, lhs, rl, lr, cube);
        }
        // Question: do we need a unification for Pi.body?
        return compare(lhs, rhs, lr, rl, null);
      });
      case FormTerm.PartTy ty -> {
        if (lhs instanceof IntroTerm.PartEl lel && rhs instanceof IntroTerm.PartEl rel)
          yield comparePartial(lel, rel, ty, lr, rl);
        else yield false;
      }
    };
    traceExit();
    return ret;
  }

  private boolean compareLambdaBody(IntroTerm.Lambda lambda, Term rhs, Sub lr, Sub rl, FormTerm.Pi pi) {
    var arg = pi.param().toArg();
    rl.map.put(pi.param().ref(), lambda.param().toTerm());
    var result = ctx.with(lambda.param(), () ->
      compare(ElimTerm.make(lambda, arg), ElimTerm.make(rhs, arg), lr, rl, pi.body()));
    rl.map.remove(pi.param().ref());
    return result;
  }

  private boolean comparePathLamBody(IntroTerm.PathLam lambda, Term rhs, Sub lr, Sub rl, FormTerm.Cube cube) {
    cube.params().zipView(lambda.params()).forEach(p ->
      rl.map.put(p._1, new RefTerm(p._2)));
    var result = ctx.withIntervals(lambda.params().view(), () ->
      compare(cube.applyDimsTo(lambda), cube.applyDimsTo(rhs), lr, rl, cube.type()));
    rl.map.removeAll(cube.params());
    return result;
  }

  private boolean comparePartial(
    @NotNull IntroTerm.PartEl lhs, @NotNull IntroTerm.PartEl rhs,
    @NotNull FormTerm.PartTy type, Sub lr, Sub rl
  ) {
    record P(Partial<Term> l, Partial<Term> r) {}
    return switch (new P(lhs.partial(), rhs.partial())) {
      case P(Partial.Const<Term>(var ll),Partial.Const<Term>(var rr)) -> compare(ll, rr, lr, rl, type.type());
      case P(Partial.Split<Term> ll,Partial.Split<Term> rr) -> CofThy.conv(type.restr(), new Subst(),
        subst -> compare(lhs.subst(subst), rhs.subst(subst), lr, rl, type.subst(subst)));
      default -> false;
    };
  }

  private boolean compareCube(@NotNull FormTerm.Cube lhs, @NotNull FormTerm.Cube rhs, Sub lr, Sub rl) {
    return TermComparator.withIntervals(lhs.params(), rhs.params(), lr, rl, rhs.params(), (lsub, rsub) -> {
      var lPar = (IntroTerm.PartEl) new IntroTerm.PartEl(lhs.partial(), lhs.type()).subst(lsub);
      var rPar = (IntroTerm.PartEl) new IntroTerm.PartEl(rhs.partial(), rhs.type()).subst(rsub);
      var lType = new FormTerm.PartTy(lPar.rhsType(), lPar.partial().restr());
      var rType = new FormTerm.PartTy(rPar.rhsType(), rPar.partial().restr());
      if (!compare(lType, rType, lr, rl, null)) return false;
      return comparePartial(lPar, rPar, lType, lr, rl);
    });
  }

  private boolean compareRestr(@NotNull Restr<Term> lhs, @NotNull Restr<Term> rhs) {
    return CofThy.conv(lhs, new Subst(), s -> CofThy.satisfied(s.restr(state, rhs)))
      && CofThy.conv(rhs, new Subst(), s -> CofThy.satisfied(s.restr(state, lhs)));
  }

  private Term doCompareUntyped(@NotNull Term preLhs, @NotNull Term preRhs, Sub lr, Sub rl) {
    traceEntrance(new Trace.UnifyT(preLhs.freezeHoles(state),
      preRhs.freezeHoles(state), this.pos));
    var ret = switch (preLhs) {
      default ->
        throw new InternalException(preLhs.getClass() + ": " + preLhs.toDoc(DistillerOptions.debug()).debugRender());
      case RefTerm.MetaPat metaPat -> {
        var lhsRef = metaPat.ref();
        if (preRhs instanceof RefTerm.MetaPat(var rRef) && lhsRef == rRef) yield lhsRef.type();
        else yield null;
      }
      case RefTerm(var lhs) -> preRhs instanceof RefTerm(var rhs) && lhs == rhs ? ctx.get(lhs) : null;
      case ElimTerm.App(var lOf,var lArg) -> {
        if (!(preRhs instanceof ElimTerm.App(var rOf,var rArg))) yield null;
        var preFnType = compareUntyped(lOf, rOf, lr, rl);
        if (!(preFnType instanceof FormTerm.Pi fnType)) yield null;
        if (!compare(lArg.term(), rArg.term(), lr, rl, fnType.param().type())) yield null;
        yield fnType.substBody(lArg.term());
      }
      case ElimTerm.PathApp lhs -> {
        if (!(preRhs instanceof ElimTerm.PathApp rhs)) yield null;
        var prePathType = compareUntyped(lhs.of(), rhs.of(), lr, rl);
        if (!(prePathType instanceof FormTerm.Path(var cube))) yield null;
        var happy = lhs.args().zipView(rhs.args()).allMatch(t ->
          compare(t._1.term(), t._2.term(), lr, rl, null));
        yield happy ? cube.type() : null;
      }
      case ElimTerm.Proj lhs -> {
        if (!(preRhs instanceof ElimTerm.Proj(var rof,var rix))) yield null;
        var preTupType = compareUntyped(lhs.of(), rof, lr, rl);
        if (!(preTupType instanceof FormTerm.Sigma(var tele))) yield null;
        if (lhs.ix() != rix) yield null;
        var params = tele.view();
        var subst = new Subst(MutableMap.create());
        for (int i = 1; i < lhs.ix(); i++) {
          var l = ElimTerm.proj(lhs, i);
          var currentParam = params.first();
          subst.add(currentParam.ref(), l);
          params = params.drop(1);
        }
        if (params.isNotEmpty()) yield params.first().subst(subst).type();
        yield params.last().subst(subst).type();
      }
      case ErrorTerm term -> ErrorTerm.typeOf(term.freezeHoles(state));
      case FormTerm.Pi(var lParam,var lBody) -> {
        if (!(preRhs instanceof FormTerm.Pi(var rParam,var rBody))) yield null;
        yield checkParam(lParam, rParam, null, new Subst(), new Subst(), lr, rl, () -> null, (lsub, rsub) -> {
          var bodyIsOk = compare(lBody.subst(lsub), rBody.subst(rsub), lr, rl, null);
          if (!bodyIsOk) return null;
          return FormTerm.Sort.Type0;
        });
      }
      case FormTerm.Sigma(var lParams) -> {
        if (!(preRhs instanceof FormTerm.Sigma(var rParams))) yield null;
        yield checkParams(lParams.view(), rParams.view(), lr, rl, () -> null, (lsub, rsub) -> FormTerm.Sort.Type0);
      }
      case FormTerm.Sort lhs -> {
        if (!(preRhs instanceof FormTerm.Sort rhs)) yield null;
        if (!compareSort(lhs, rhs)) yield null;
        yield (cmp == Ordering.Lt ? lhs : rhs).succ();
      }
      case FormTerm.PartTy(var lTy,var lR) -> {
        if (!(preRhs instanceof FormTerm.PartTy(var rTy,var rR))) yield null;
        var happy = compare(lTy, rTy, lr, rl, null)
          && compareRestr(lR, rR);
        yield happy ? FormTerm.Sort.Type0 : null;
      }
      case FormTerm.Path(var lCube) -> {
        if (!(preRhs instanceof FormTerm.Path(var rCube))) yield null;
        yield compareCube(lCube, rCube, lr, rl) ? FormTerm.Sort.Type0 : null;
      }
      case PrimTerm.Interval lhs -> preRhs instanceof PrimTerm.Interval ? FormTerm.Sort.Type0 : null;
      case PrimTerm.Mula lhs -> {
        if (!(preRhs instanceof PrimTerm.Mula rhs)) yield null;
        if (compareRestr(CofThy.isOne(lhs), CofThy.isOne(rhs)))
          yield PrimTerm.Interval.INSTANCE;
        else yield null;
      }
      // See compareApprox for why we don't compare these
      case CallTerm.Fn lhs -> null;
      case CallTerm.Data lhs -> {
        if (!(preRhs instanceof CallTerm.Data rhs) || lhs.ref() != rhs.ref()) yield null;
        var args = visitArgs(lhs.args(), rhs.args(), lr, rl, Term.Param.subst(Def.defTele(lhs.ref()), lhs.ulift()));
        // Do not need to be computed precisely because unification won't need this info
        yield args ? FormTerm.Sort.Type0 : null;
      }
      case CallTerm.Struct lhs -> {
        if (!(preRhs instanceof CallTerm.Struct rhs) || lhs.ref() != rhs.ref()) yield null;
        var args = visitArgs(lhs.args(), rhs.args(), lr, rl, Term.Param.subst(Def.defTele(lhs.ref()), lhs.ulift()));
        yield args ? FormTerm.Sort.Type0 : null;
      }
      case PrimTerm.Coe lhs -> {
        if (!(preRhs instanceof PrimTerm.Coe rhs)) yield null;
        if (!compareRestr(lhs.restr(), rhs.restr())) yield null;
        yield compare(lhs.type(), rhs.type(), lr, rl, PrimDef.intervalToA()) ?
          PrimDef.familyLeftToRight(lhs.type()) : null;
      }
      case CallTerm.Con lhs -> switch (preRhs) {
        case CallTerm.Con rhs -> {
          if (lhs.ref() != rhs.ref()) yield null;
          var retType = getType(lhs, lhs.ref());
          // Lossy comparison
          if (visitArgs(lhs.conArgs(), rhs.conArgs(), lr, rl, Term.Param.subst(CtorDef.conTele(lhs.ref()), lhs.ulift())))
            yield retType;
          yield null;
        }
        case LitTerm.ShapedInt rhs -> compareUntyped(lhs, rhs.constructorForm(state), lr, rl);
        case LitTerm.ShapedList rhs -> compareUntyped(lhs, rhs.constructorForm(state), lr, rl);
        default -> null;
      };
      case CallTerm.Prim lhs -> null;
      case CallTerm.Access lhs -> {
        if (!(preRhs instanceof CallTerm.Access rhs)) yield null;
        var preStructType = compareUntyped(lhs.of(), rhs.of(), lr, rl);
        if (!(preStructType instanceof CallTerm.Struct structType)) yield null;
        if (lhs.ref() != rhs.ref()) yield null;
        yield Def.defResult(lhs.ref());
      }
      case LitTerm.ShapedInt lhs -> switch (preRhs) {
        case LitTerm.ShapedInt rhs -> {
          if (!lhs.compareShape(this, rhs)) yield null;
          if (!lhs.compareUntyped(rhs)) yield null;
          yield lhs.type(); // What about rhs.type()? A: sameValue implies same type
        }
        case CallTerm.Con rhs -> compareUntyped(lhs.constructorForm(state), rhs, lr, rl);
        default -> null;
      };
      case LitTerm.ShapedList lhs -> switch (preRhs) {
        case LitTerm.ShapedList rhs -> {
          if (!lhs.compareShape(this, rhs)) yield null;
          if (!lhs.compareUntyped(rhs,
            (l, r) -> compare(l, r, lr, rl, null))) yield null;

          yield lhs.type();
        }

        case CallTerm.Con rhs -> compareUntyped(lhs.constructorForm(state), rhs, lr, rl);
        default -> null;
      };
      case CallTerm.Hole lhs -> solveMeta(preRhs, lr, rl, lhs);
    };
    traceExit();
    return ret;
  }

  protected abstract @Nullable Term solveMeta(@NotNull Term preRhs, Sub lr, Sub rl, @NotNull CallTerm.Hole lhs);

  public boolean compareSort(FormTerm.Sort l, FormTerm.Sort r) {
    var result = switch (cmp) {
      case Gt -> TermComparator.sortLt(r, l);
      case Eq -> l.kind() == r.kind() && l.lift() == r.lift();
      case Lt -> TermComparator.sortLt(l, r);
    };
    if (!result) {
      switch (cmp) {
        case Eq -> reporter.report(new LevelError(pos, l, r, true));
        case Gt -> reporter.report(new LevelError(pos, l, r, false));
        case Lt -> reporter.report(new LevelError(pos, r, l, false));
      }
    }
    return result;
  }

  @Debug.Renderer(childrenArray = "map.toArray()", hasChildren = "!map.isEmpty()")
  public record Sub(@NotNull MutableMap<@NotNull AnyVar, @NotNull RefTerm> map) implements Cloneable {
    public Sub() {
      this(MutableMap.create());
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod") public @NotNull Sub clone() {
      return new Sub(MutableMap.from(map));
    }
  }

  public record FailureData(@NotNull Term lhs, @NotNull Term rhs) {
  }
}
