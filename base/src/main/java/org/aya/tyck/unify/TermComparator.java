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
import org.aya.generic.util.InternalException;
import org.aya.generic.util.NormalizeMode;
import org.aya.guest0x0.cubical.CofThy;
import org.aya.guest0x0.cubical.Formula;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
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

  public static <E> E withIntervals(SeqView<LocalVar> l, SeqView<LocalVar> r, Sub lr, Sub rl, Supplier<E> supplier) {
    assert l.sizeEquals(r);
    for (var conv : l.zipView(r)) {
      lr.map.put(conv._1, new RefTerm(conv._2));
      rl.map.put(conv._2, new RefTerm(conv._1));
    }
    var res = supplier.get();
    l.forEach(lr.map::remove);
    r.forEach(rl.map::remove);
    return res;
  }

  private static boolean sortLt(FormTerm.Sort l, FormTerm.Sort r) {
    return switch (l) {
      case FormTerm.Type lt -> switch (r) {
        case FormTerm.Type rt -> lt.lift() <= rt.lift();
        case FormTerm.Set rt -> lt.lift() <= rt.lift();
        case FormTerm.ISet iSet -> false;
        case FormTerm.Prop prop -> false;
      };
      case FormTerm.ISet iSet -> switch (r) {
        case FormTerm.ISet set -> true;
        case FormTerm.Prop prop -> false;
        case FormTerm.Set set -> true;
        case FormTerm.Type type -> false;
      };
      case FormTerm.Prop prop -> switch (r) {
        case FormTerm.ISet iSet -> false;
        case FormTerm.Prop prop1 -> true;
        case FormTerm.Set set -> false;
        case FormTerm.Type type -> false;
      };
      case FormTerm.Set lt -> switch (r) {
        case FormTerm.ISet iSet -> false;
        case FormTerm.Prop prop -> false;
        case FormTerm.Set rt -> lt.lift() <= rt.lift();
        case FormTerm.Type rt -> false;
      };
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
    Term.Param l, Term.Param r, @Nullable Term type,
    Sub lr, Sub rl, Supplier<T> fail, Supplier<T> success
  ) {
    if (l.explicit() != r.explicit()) return fail.get();
    if (!compare(l.type(), r.type(), lr, rl, type)) return fail.get();
    // Do not substitute when one side is ignored
    if (l.ref() != LocalVar.IGNORED && r.ref() != LocalVar.IGNORED) {
      rl.map.put(r.ref(), l.toTerm());
      lr.map.put(l.ref(), r.toTerm());
    }
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
    return checkParam(l.first(), r.first(), null, lr, rl, fail, () ->
      checkParams(l.drop(1), r.drop(1), lr, rl, fail, success));
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
    @NotNull DefVar<? extends Def, ? extends Decl.Telescopic> lhsRef, int ulift
  ) {
    var retType = getType(lhs, lhsRef);
    // Lossy comparison
    if (visitArgs(lhs.args(), rhs.args(), lr, rl,
      Term.Param.subst(Def.defTele(lhsRef), ulift))) return retType;
    if (compareWHNF(lhs, rhs, lr, rl, retType)) return retType;
    else return null;
  }

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
      case FormTerm.Sigma sigma -> {
        var params = sigma.params().view();
        for (int i = 1, size = sigma.params().size(); i <= size; i++) {
          var l = ElimTerm.proj(lhs, i);
          var currentParam = params.first();
          ctx.put(currentParam);
          if (!compare(l, ElimTerm.proj(rhs, i), lr, rl, currentParam.type())) yield false;
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
            var res = compare(lambda.body(), rambda.body(), lr, rl, pi.body());
            lr.map.remove(lambda.param().ref());
            rl.map.remove(rambda.param().ref());
            return res;
          });
          return compareLambdaBody(rhs, lr, rl, lambda, pi);
        });
        if (rhs instanceof IntroTerm.Lambda rambda) return ctx.with(rambda.param(),
          () -> compareLambdaBody(lhs, rl, lr, rambda, pi));
        // Question: do we need a unification for Pi.body?
        return compare(lhs, rhs, lr, rl, null);
      });
      // In this case, both sides have the same type (I hope)
      case FormTerm.Path path -> ctx.withIntervals(path.cube().params().view(), () -> {
        if (lhs instanceof IntroTerm.PathLam lambda) return ctx.withIntervals(lambda.params().view(), () -> {
          if (rhs instanceof IntroTerm.PathLam rambda) return ctx.withIntervals(rambda.params().view(), () ->
            TermComparator.withIntervals(lambda.params().view(), rambda.params().view(), lr, rl, () ->
              compare(lambda.body(), rambda.body(), lr, rl, path.cube().type())));
          return comparePathLamBody(rhs, lr, rl, lambda, path.cube());
        });
        if (rhs instanceof IntroTerm.PathLam rambda) return ctx.withIntervals(rambda.params().view(), () ->
          comparePathLamBody(lhs, rl, lr, rambda, path.cube()));
        // Question: do we need a unification for Pi.body?
        return compare(lhs, rhs, lr, rl, null);
      });
      case FormTerm.PartTy ty when lhs instanceof IntroTerm.PartEl lel && rhs instanceof IntroTerm.PartEl rel ->
        comparePartial(lel, rel, ty, lr, rl);
      case FormTerm.PartTy ty -> false;
    };
    traceExit();
    return ret;
  }

  private boolean compareLambdaBody(Term rhs, Sub lr, Sub rl, IntroTerm.Lambda lambda, FormTerm.Pi pi) {
    return ctx.with(lambda.param(), () ->
      compare(lambda.body(), CallTerm.make(rhs, lambda.param().toArg()), lr, rl, pi.body()));
  }

  private boolean comparePathLamBody(Term rhs, Sub lr, Sub rl, IntroTerm.PathLam lambda, FormTerm.Cube cube) {
    // This is not necessarily correct, but we don't need to generate a precise cube
    // as conversion for PathApp does not compare cubes (it doesn't have to)
    var constructed = new FormTerm.Cube(lambda.params(), cube.type(), new Partial.Split<>(ImmutableSeq.empty()));
    return ctx.withIntervals(lambda.params().view(), () ->
      compare(lambda.body(), constructed.applyDimsTo(rhs), lr, rl, cube.type()));
  }

  private boolean comparePartial(
    @NotNull IntroTerm.PartEl lhs, @NotNull IntroTerm.PartEl rhs,
    @Nullable FormTerm.PartTy type, Sub lr, Sub rl
  ) {
    return switch (lhs.partial()) {
      case Partial.Const<Term> ll when rhs.partial() instanceof Partial.Const<Term> rr ->
        compare(ll.u(), rr.u(), lr, rl, type == null ? null : type.type());
      case Partial.Split<Term> ll when rhs.partial() instanceof Partial.Split<Term> rr ->
        CofThy.conv(type == null ? ll.restr() : type.restr(), new Subst(),
          subst -> compare(lhs.subst(subst), rhs.subst(subst), lr, rl, type == null ? null : type.subst(subst)));
      default -> false;
    };
  }

  private boolean compareCube(@NotNull FormTerm.Cube lhs, @NotNull FormTerm.Cube rhs, Sub lr, Sub rl) {
    return TermComparator.withIntervals(lhs.params().view(), rhs.params().view(), lr, rl, () -> {
      // TODO: let CofThy.propExt uses lr and rl?
      var lPar = (IntroTerm.PartEl) new IntroTerm.PartEl(lhs.partial(), lhs.type().subst(lr.map)).subst(lr.map);
      var rPar = new IntroTerm.PartEl(rhs.partial(), rhs.type());
      var lType = new FormTerm.PartTy(lPar.rhsType(), lPar.partial().restr());
      var rType = new FormTerm.PartTy(rPar.rhsType(), rPar.partial().restr());
      if (!compare(lType, rType, lr, rl, null)) return false;
      return comparePartial(lPar, rPar, lType, lr, rl);
    });
  }

  private boolean compareRestr(@NotNull Restr<Term> lhs, @NotNull Restr<Term> rhs) {
    return CofThy.propExt(new Subst(), lhs, rhs, (sub, term) -> sub.restr(state, term));
  }

  private Term doCompareUntyped(@NotNull Term preLhs, @NotNull Term preRhs, Sub lr, Sub rl) {
    traceEntrance(new Trace.UnifyT(preLhs.freezeHoles(state),
      preRhs.freezeHoles(state), this.pos));
    var ret = switch (preLhs) {
      default ->
        throw new InternalException(preLhs.getClass() + ": " + preLhs.toDoc(DistillerOptions.debug()).debugRender());
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
      case ElimTerm.PathApp lhs -> {
        if (!(preRhs instanceof ElimTerm.PathApp rhs)) yield null;
        var prePathType = compareUntyped(lhs.of(), rhs.of(), lr, rl);
        if (!(prePathType instanceof FormTerm.Path path)) yield null;
        var happy = lhs.args().zipView(rhs.args()).allMatch(t ->
          compare(t._1.term(), t._2.term(), lr, rl, null));
        yield happy ? path.cube().type() : null;
      }
      case ElimTerm.Proj lhs -> {
        if (!(preRhs instanceof ElimTerm.Proj rhs)) yield null;
        var preTupType = compareUntyped(lhs.of(), rhs.of(), lr, rl);
        if (!(preTupType instanceof FormTerm.Sigma tupType)) yield null;
        if (lhs.ix() != rhs.ix()) yield null;
        var params = tupType.params().view();
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
      case FormTerm.Pi lhs -> {
        if (!(preRhs instanceof FormTerm.Pi rhs)) yield null;
        yield checkParam(lhs.param(), rhs.param(), null, lr, rl, () -> null, () -> {
          var bodyIsOk = compare(lhs.body(), rhs.body(), lr, rl, null);
          if (!bodyIsOk) return null;
          return FormTerm.Type.ZERO;
        });
      }
      case FormTerm.Sigma lhs -> {
        if (!(preRhs instanceof FormTerm.Sigma rhs)) yield null;
        yield checkParams(lhs.params().view(), rhs.params().view(), lr, rl, () -> null, () -> FormTerm.Type.ZERO);
      }
      case FormTerm.Sort lhs -> {
        if (!(preRhs instanceof FormTerm.Sort rhs)) yield null;
        if (!compareSort(lhs, rhs)) yield null;
        yield (cmp == Ordering.Lt ? lhs : rhs).succ();
      }
      case FormTerm.PartTy lhs -> {
        if (!(preRhs instanceof FormTerm.PartTy rhs)) yield null;
        var happy = compare(lhs.type(), rhs.type(), lr, rl, null)
          && compareRestr(lhs.restr(), rhs.restr());
        yield happy ? FormTerm.Type.ZERO : null;
      }
      case FormTerm.Path lhs -> {
        if (!(preRhs instanceof FormTerm.Path rhs)) yield null;
        yield compareCube(lhs.cube(), rhs.cube(), lr, rl) ? FormTerm.Type.ZERO : null;
      }
      case PrimTerm.Interval lhs -> preRhs instanceof PrimTerm.Interval ? FormTerm.Type.ZERO : null;
      case PrimTerm.Mula lhs -> {
        if (!(preRhs instanceof PrimTerm.Mula rhs)) yield null;
        var happy = switch (lhs.asFormula()) {
          case Formula.Lit<Term> ll when rhs.asFormula() instanceof Formula.Lit<Term> rr -> ll.isOne() == rr.isOne();
          case Formula.Inv<Term> ll when rhs.asFormula() instanceof Formula.Inv<Term> rr ->
            compare(ll.i(), rr.i(), lr, rl, null);
          case Formula.Conn<Term> ll when rhs.asFormula() instanceof Formula.Conn<Term> rr -> ll.isAnd() == rr.isAnd()
            && compare(ll.l(), rr.l(), lr, rl, null)
            && compare(ll.r(), rr.r(), lr, rl, null);
          default -> false;
        };
        yield happy ? PrimTerm.Interval.INSTANCE : null;
      }
      // See compareApprox for why we don't compare these
      case CallTerm.Fn lhs -> null;
      case CallTerm.Data lhs -> {
        if (!(preRhs instanceof CallTerm.Data rhs) || lhs.ref() != rhs.ref()) yield null;
        var args = visitArgs(lhs.args(), rhs.args(), lr, rl, Term.Param.subst(Def.defTele(lhs.ref()), lhs.ulift()));
        // Do not need to be computed precisely because unification won't need this info
        yield args ? FormTerm.Type.ZERO : null;
      }
      case CallTerm.Struct lhs -> {
        if (!(preRhs instanceof CallTerm.Struct rhs) || lhs.ref() != rhs.ref()) yield null;
        var args = visitArgs(lhs.args(), rhs.args(), lr, rl, Term.Param.subst(Def.defTele(lhs.ref()), lhs.ulift()));
        yield args ? FormTerm.Type.ZERO : null;
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
          if (!lhs.sameValue(state, rhs)) yield null;
          yield lhs.type(); // What about rhs.type()? A: sameValue implies same type
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
