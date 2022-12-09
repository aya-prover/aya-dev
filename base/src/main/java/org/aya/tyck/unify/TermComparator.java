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
import org.aya.core.visitor.AyaRestrSimplifier;
import org.aya.core.visitor.Subst;
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
import org.aya.util.Arg;
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
    return term instanceof FnCall || term instanceof ConCall || term instanceof PrimCall;
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

  private static boolean sortLt(SortTerm l, SortTerm r) {
    var lift = l.lift();
    var rift = r.lift();
    return switch (l.kind()) {
      case Type -> switch (r.kind()) {
        case Type, Set -> lift <= rift;
        case default -> false;
      };
      case ISet -> switch (r.kind()) {
        case ISet, Set -> true;
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
    if (rhs instanceof MetaTerm) return compareUntyped(rhs, lhs, rl, lr) != null;
    // ^ Beware of the order!!
    if (lhs instanceof MetaTerm || type == null) return compareUntyped(lhs, rhs, lr, rl) != null;
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
      case FnCall lhs when preRhs instanceof FnCall rhs ->
        lhs.ref() != rhs.ref() ? null : visitCall(lhs, rhs, lr, rl, lhs.ref(), lhs.ulift());
      case ConCall lhs when preRhs instanceof ConCall rhs ->
        lhs.ref() != rhs.ref() ? null : visitCall(lhs, rhs, lr, rl, lhs.ref(), lhs.ulift());
      case PrimCall lhs when preRhs instanceof PrimCall rhs ->
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
    @NotNull Callable lhs, @NotNull Callable rhs, Sub lr, Sub rl,
    @NotNull DefVar<? extends Def, ? extends Decl.Telescopic<?>> lhsRef, int ulift
  ) {
    var retType = getType(lhs, lhsRef);
    // Lossy comparison
    if (visitArgs(lhs.args(), rhs.args(), lr, rl,
      Term.Param.subst(Def.defTele(lhsRef), ulift))) return retType;
    if (compareWHNF(lhs, rhs, lr, rl, retType)) return retType;
    else return null;
  }

  private record Pair(Term lhs, Term rhs) {}

  private @NotNull Term getType(@NotNull Callable lhs, @NotNull DefVar<? extends Def, ? extends Decl.Telescopic<?>> lhsRef) {
    var substMap = MutableMap.<AnyVar, Term>create();
    for (var pa : lhs.args().view().zip(Def.defTele(lhsRef))) {
      substMap.set(pa._2.ref(), pa._1.term());
    }
    return Def.defResult(lhsRef).subst(substMap);
  }

  private boolean doCompareTyped(@NotNull Term type, @NotNull Term lhs, @NotNull Term rhs, Sub lr, Sub rl) {
    traceEntrance(new Trace.UnifyT(lhs.freezeHoles(state), rhs.freezeHoles(state),
      pos, type.freezeHoles(state)));
    var ret = switch (type) {
      default -> compareUntyped(lhs, rhs, lr, rl) != null;
      case StructCall type1 -> {
        var fieldSigs = type1.ref().core.fields;
        var paramSubst = Def.defTele(type1.ref()).view().zip(type1.args().view()).map(x ->
          Tuple.of(x._1.ref(), x._2.term())).<AnyVar, Term>toImmutableMap();
        var fieldSubst = new Subst(MutableHashMap.create());
        for (var fieldSig : fieldSigs) {
          var dummyVars = fieldSig.selfTele.map(par -> par.ref().rename());
          var dummy = dummyVars.zip(fieldSig.selfTele).map(vpa ->
            new Arg<Term>(new RefTerm(vpa._1), vpa._2.explicit()));
          var l = new FieldTerm(lhs, fieldSig.ref(), type1.args(), dummy);
          var r = new FieldTerm(rhs, fieldSig.ref(), type1.args(), dummy);
          fieldSubst.add(fieldSig.ref(), l);
          if (!compare(l, r, lr, rl, fieldSig.result().subst(paramSubst).subst(fieldSubst))) yield false;
        }
        yield true;
      }
      case LamTerm $ -> throw new InternalException("LamTerm is never type");
      case ConCall $ -> throw new InternalException("ConCall is never type");
      case TupTerm $ -> throw new InternalException("TupTerm is never type");
      case NewTerm $ -> throw new InternalException("NewTerm is never type");
      case ErrorTerm $ -> true;
      case SigmaTerm(var paramsSeq) -> {
        var params = paramsSeq.view();
        for (int i = 1, size = paramsSeq.size(); i <= size; i++) {
          var l = ProjTerm.proj(lhs, i);
          var currentParam = params.first();
          ctx.put(currentParam);
          if (!compare(l, ProjTerm.proj(rhs, i), lr, rl, currentParam.type())) yield false;
          params = params.drop(1).map(x -> x.subst(currentParam.ref(), l));
        }
        ctx.remove(paramsSeq.view().map(Term.Param::ref));
        yield true;
      }
      case PiTerm pi -> ctx.with(pi.param(), () -> switch (new Pair(lhs, rhs)) {
        case Pair(LamTerm(var lp, var lb), LamTerm(var rp, var rb)) -> {
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
        case Pair(var $, LamTerm rambda) -> compareLambdaBody(rambda, lhs, rl, lr, pi);
        case Pair(LamTerm lambda, var $) -> compareLambdaBody(lambda, rhs, lr, rl, pi);
        // Question: do we need a unification for Pi.body?
        case default -> compare(lhs, rhs, lr, rl, null);
      });
      // In this case, both sides have the same type (I hope)
      case PathTerm cube -> ctx.withIntervals(cube.params().view(), () -> {
        if (lhs instanceof PLamTerm lambda) {
          assert lambda.params().sizeEquals(cube.params());
          if (rhs instanceof PLamTerm(var rparams, var rbody)) {
            assert rparams.sizeEquals(cube.params());
            withIntervals(lambda.params(), rparams, lr, rl, cube.params(), (lsub, rsub) ->
              compare(lambda.body().subst(lsub), rbody.subst(rsub), lr, rl, cube.type()));
          }
          return comparePathLamBody(lambda, rhs, lr, rl, cube);
        }
        if (rhs instanceof PLamTerm rambda) {
          assert rambda.params().sizeEquals(cube.params());
          return comparePathLamBody(rambda, lhs, rl, lr, cube);
        }
        // Question: do we need a unification for Pi.body?
        return compare(lhs, rhs, lr, rl, null);
      });
      case PartialTyTerm ty -> {
        if (lhs instanceof PartialTerm lel && rhs instanceof PartialTerm rel)
          yield comparePartial(lel, rel, ty, lr, rl);
        else yield false;
      }
    };
    traceExit();
    return ret;
  }

  private boolean compareLambdaBody(LamTerm lambda, Term rhs, Sub lr, Sub rl, PiTerm pi) {
    var arg = pi.param().toArg();
    rl.map.put(pi.param().ref(), lambda.param().toTerm());
    var result = ctx.with(lambda.param(), () ->
      compare(AppTerm.make(lambda, arg), AppTerm.make(rhs, arg), lr, rl, pi.body()));
    rl.map.remove(pi.param().ref());
    return result;
  }

  private boolean comparePathLamBody(PLamTerm lambda, Term rhs, Sub lr, Sub rl, PathTerm cube) {
    cube.params().forEachWith(lambda.params(), (a, b) -> rl.map.put(a, new RefTerm(b)));
    var result = ctx.withIntervals(lambda.params().view(), () ->
      compare(cube.applyDimsTo(lambda), cube.applyDimsTo(rhs), lr, rl, cube.type()));
    rl.map.removeAll(cube.params());
    return result;
  }

  private boolean comparePartial(
    @NotNull PartialTerm lhs, @NotNull PartialTerm rhs,
    @NotNull PartialTyTerm type, Sub lr, Sub rl
  ) {
    record P(Partial<Term> l, Partial<Term> r) {}
    return switch (new P(lhs.partial(), rhs.partial())) {
      case P(Partial.Const<Term>(var ll), Partial.Const<Term>(var rr)) -> compare(ll, rr, lr, rl, type.type());
      case P(Partial.Split<Term> ll, Partial.Split<Term> rr) -> CofThy.conv(type.restr(), new Subst(),
        subst -> compare(lhs.subst(subst), rhs.subst(subst), lr, rl, type.subst(subst)));
      default -> false;
    };
  }

  private boolean compareCube(@NotNull PathTerm lhs, @NotNull PathTerm rhs, Sub lr, Sub rl) {
    var tyVars = rhs.params();
    return ctx.withIntervals(tyVars.view(), () -> TermComparator.withIntervals(lhs.params(), rhs.params(), lr, rl, tyVars, (lsub, rsub) -> {
      var lPar = (PartialTerm) new PartialTerm(lhs.partial(), lhs.type()).subst(lsub);
      var rPar = (PartialTerm) new PartialTerm(rhs.partial(), rhs.type()).subst(rsub);
      var lType = new PartialTyTerm(lPar.rhsType(), lPar.partial().restr());
      var rType = new PartialTyTerm(rPar.rhsType(), rPar.partial().restr());
      if (!compare(lType, rType, lr, rl, null)) return false;
      return comparePartial(lPar, rPar, lType, lr, rl);
    }));
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
      case MetaPatTerm metaPat -> {
        var lhsRef = metaPat.ref();
        if (preRhs instanceof MetaPatTerm(var rRef) && lhsRef == rRef) yield lhsRef.type();
        else yield null;
      }
      case RefTerm(var lhs) -> preRhs instanceof RefTerm(var rhs) && lhs == rhs ? ctx.get(lhs) : null;
      case AppTerm(var lOf, var lArg) -> {
        if (!(preRhs instanceof AppTerm(var rOf, var rArg))) yield null;
        var preFnType = compareUntyped(lOf, rOf, lr, rl);
        if (!(preFnType instanceof PiTerm fnType)) yield null;
        if (!compare(lArg.term(), rArg.term(), lr, rl, fnType.param().type())) yield null;
        yield fnType.substBody(lArg.term());
      }
      case PAppTerm lhs -> {
        if (!(preRhs instanceof PAppTerm rhs)) yield null;
        var prePathType = compareUntyped(lhs.of(), rhs.of(), lr, rl);
        if (!(prePathType instanceof PathTerm cube)) yield null;
        var happy = lhs.args().allMatchWith(rhs.args(), (l, r) ->
          compare(l.term(), r.term(), lr, rl, null));
        yield happy ? cube.type() : null;
      }
      case ProjTerm lhs -> {
        if (!(preRhs instanceof ProjTerm(var rof, var rix))) yield null;
        var preTupType = compareUntyped(lhs.of(), rof, lr, rl);
        if (!(preTupType instanceof SigmaTerm(var tele))) yield null;
        if (lhs.ix() != rix) yield null;
        var params = tele.view();
        var subst = new Subst(MutableMap.create());
        for (int i = 1; i < lhs.ix(); i++) {
          var l = ProjTerm.proj(lhs, i);
          var currentParam = params.first();
          subst.add(currentParam.ref(), l);
          params = params.drop(1);
        }
        if (params.isNotEmpty()) yield params.first().subst(subst).type();
        yield params.last().subst(subst).type();
      }
      case ErrorTerm term -> ErrorTerm.typeOf(term.freezeHoles(state));
      case PiTerm(var lParam, var lBody) -> {
        if (!(preRhs instanceof PiTerm(var rParam, var rBody))) yield null;
        yield checkParam(lParam, rParam, null, new Subst(), new Subst(), lr, rl, () -> null, (lsub, rsub) -> {
          var bodyIsOk = compare(lBody.subst(lsub), rBody.subst(rsub), lr, rl, null);
          if (!bodyIsOk) return null;
          return SortTerm.Type0;
        });
      }
      case SigmaTerm(var lParams) -> {
        if (!(preRhs instanceof SigmaTerm(var rParams))) yield null;
        yield checkParams(lParams.view(), rParams.view(), lr, rl, () -> null, (lsub, rsub) -> SortTerm.Type0);
      }
      case SortTerm lhs -> {
        if (!(preRhs instanceof SortTerm rhs)) yield null;
        if (!compareSort(lhs, rhs)) yield null;
        yield (cmp == Ordering.Lt ? lhs : rhs).succ();
      }
      case PartialTyTerm(var lTy, var lR) -> {
        if (!(preRhs instanceof PartialTyTerm(var rTy, var rR))) yield null;
        var happy = compare(lTy, rTy, lr, rl, null)
          && compareRestr(lR, rR);
        yield happy ? SortTerm.Type0 : null;
      }
      case PathTerm lCube -> {
        if (!(preRhs instanceof PathTerm rCube)) yield null;
        yield compareCube(lCube, rCube, lr, rl) ? SortTerm.Type0 : null;
      }
      case IntervalTerm lhs -> preRhs instanceof IntervalTerm ? SortTerm.Type0 : null;
      case FormulaTerm lhs -> {
        if (!(preRhs instanceof FormulaTerm rhs)) yield null;
        if (compareRestr(AyaRestrSimplifier.INSTANCE.isOne(lhs), AyaRestrSimplifier.INSTANCE.isOne(rhs)))
          yield IntervalTerm.INSTANCE;
        else yield null;
      }
      // See compareApprox for why we don't compare these
      case FnCall lhs -> null;
      case DataCall lhs -> {
        if (!(preRhs instanceof DataCall rhs) || lhs.ref() != rhs.ref()) yield null;
        var args = visitArgs(lhs.args(), rhs.args(), lr, rl, Term.Param.subst(Def.defTele(lhs.ref()), lhs.ulift()));
        // Do not need to be computed precisely because unification won't need this info
        yield args ? SortTerm.Type0 : null;
      }
      case StructCall lhs -> {
        if (!(preRhs instanceof StructCall rhs) || lhs.ref() != rhs.ref()) yield null;
        var args = visitArgs(lhs.args(), rhs.args(), lr, rl, Term.Param.subst(Def.defTele(lhs.ref()), lhs.ulift()));
        yield args ? SortTerm.Type0 : null;
      }
      case CoeTerm lhs -> {
        if (!(preRhs instanceof CoeTerm rhs)) yield null;
        if (!compareRestr(lhs.restr(), rhs.restr())) yield null;
        yield compare(lhs.type(), rhs.type(), lr, rl, PrimDef.intervalToType()) ?
          PrimDef.familyLeftToRight(lhs.type()) : null;
      }
      case ConCall lhs -> switch (preRhs) {
        case ConCall rhs -> {
          if (lhs.ref() != rhs.ref()) yield null;
          var retType = getType(lhs, lhs.ref());
          // Lossy comparison
          if (visitArgs(lhs.conArgs(), rhs.conArgs(), lr, rl, Term.Param.subst(CtorDef.conTele(lhs.ref()), lhs.ulift())))
            yield retType;
          yield null;
        }
        case IntegerTerm rhs -> compareUntyped(lhs, rhs.constructorForm(), lr, rl);
        case ListTerm rhs -> compareUntyped(lhs, rhs.constructorForm(), lr, rl);
        default -> null;
      };
      case PrimCall lhs -> null;
      case FieldTerm lhs -> {
        if (!(preRhs instanceof FieldTerm rhs)) yield null;
        var preStructType = compareUntyped(lhs.of(), rhs.of(), lr, rl);
        if (!(preStructType instanceof StructCall structType)) yield null;
        if (lhs.ref() != rhs.ref()) yield null;
        yield Def.defResult(lhs.ref());
      }
      case IntegerTerm lhs -> switch (preRhs) {
        case IntegerTerm rhs -> {
          if (!lhs.compareShape(this, rhs)) yield null;
          if (!lhs.compareUntyped(rhs)) yield null;
          yield lhs.type(); // compareShape implies lhs.type() = rhs.type()
        }
        case ConCall rhs -> compareUntyped(lhs.constructorForm(), rhs, lr, rl);
        default -> null;
      };
      case ListTerm lhs -> switch (preRhs) {
        case ListTerm rhs -> {
          if (!lhs.compareShape(this, rhs)) yield null;
          if (!lhs.compareUntyped(rhs, (l, r) -> compare(l, r, lr, rl, null))) yield null;
          yield lhs.type();
        }
        case ConCall rhs -> compareUntyped(lhs.constructorForm(), rhs, lr, rl);
        default -> null;
      };
      case MetaTerm lhs -> solveMeta(preRhs, lr, rl, lhs);
    };
    traceExit();
    return ret;
  }

  protected abstract @Nullable Term solveMeta(@NotNull Term preRhs, Sub lr, Sub rl, @NotNull MetaTerm lhs);

  public boolean compareSort(SortTerm l, SortTerm r) {
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
