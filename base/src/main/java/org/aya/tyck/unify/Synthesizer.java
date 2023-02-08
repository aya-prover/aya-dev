// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.unify;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.core.def.Def;
import org.aya.core.def.PrimDef;
import org.aya.core.meta.MetaInfo;
import org.aya.core.term.*;
import org.aya.core.visitor.DeltaExpander;
import org.aya.core.visitor.Subst;
import org.aya.generic.SortKind;
import org.aya.generic.util.InternalException;
import org.aya.generic.util.NormalizeMode;
import org.aya.guest0x0.cubical.Partial;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.tyck.env.LocalCtx;
import org.aya.tyck.tycker.TyckState;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Similar to <code>GetTypeVisitor</code> in Arend.
 *
 * @author ice1000
 * @see org.aya.tyck.unify.DoubleChecker
 */
public record Synthesizer(@NotNull TyckState state, @NotNull LocalCtx ctx) {
  public @Nullable Term tryPress(@NotNull Term preterm) {
    var synthesize = synthesize(preterm);
    return synthesize != null ? whnf(synthesize) : null;
  }

  public @NotNull Term press(@NotNull Term preterm) {
    var synthesize = tryPress(preterm);
    assert synthesize != null : preterm.toDoc(AyaPrettierOptions.pretty()).debugRender();
    return synthesize;
  }

  public boolean inheritPiDom(@NotNull Term type, @NotNull SortTerm expected) {
    if (type instanceof MetaTerm meta && meta.ref().info instanceof MetaInfo.AnyType) {
      var typed = meta.asPiDom(expected);
      return state.solve(meta.ref(), typed);
    }
    if (!(tryPress(type) instanceof SortTerm actual)) return false;
    return switch (expected.kind()) {
      case Prop -> switch (actual.kind()) {
        case Prop, Type -> true;
        case Set, ISet -> false;
      };
      case Type -> actual.kind() != SortKind.Set && actual.lift() <= expected.lift();
      case Set -> actual.lift() <= expected.lift();
      case ISet -> unreachable(type);
    };
  }

  /**
   * @param preterm expected to be beta-normalized
   * @return null if failed to synthesize
   */
  public @Nullable Term synthesize(@NotNull Term preterm) {
    return switch (preterm) {
      case RefTerm(var var) -> ctx.get(var);
      case ConCall conCall -> conCall.head().underlyingDataCall();
      case Callable.DefCall call -> Def.defResult(call.ref())
        .subst(DeltaExpander.buildSubst(Def.defTele(call.ref()), call.args()))
        .lift(call.ulift());
      case MetaTerm hole -> {
        var result = hole.ref().info.result();
        if (result == null) {
          preterm = whnf(preterm);
          yield preterm instanceof MetaTerm ? null : synthesize(preterm);
        } else yield result;
      }
      case RefTerm.Field field -> Def.defType(field.ref());
      case FieldTerm access -> {
        var callRaw = tryPress(access.of());
        if (!(callRaw instanceof ClassCall call)) yield unreachable(access);
        var field = access.ref();
        var subst = DeltaExpander.buildSubst(Def.defTele(field), access.fieldArgs())
          .add(DeltaExpander.buildSubst(Def.defTele(call.ref()), access.structArgs()));
        yield Def.defResult(field).subst(subst);
      }
      case SigmaTerm sigma -> {
        var univ = MutableList.<SortTerm>create();
        for (var param : sigma.params()) {
          var pressed = tryPress(param.type());
          if (!(pressed instanceof SortTerm sort)) yield null;
          univ.append(sort);
          ctx.put(param);
        }
        ctx.remove(sigma.params().view().map(Term.Param::ref));
        yield univ.reduce(SigmaTerm::lub);
      }
      case PiTerm pi -> {
        var paramTyRaw = tryPress(pi.param().type());
        if (!(paramTyRaw instanceof SortTerm paramTy)) yield null;
        var t = new Synthesizer(state, ctx.deriveSeq());
        yield t.ctx.with(pi.param(), () -> {
          if (t.press(pi.body()) instanceof SortTerm retTy) {
            return PiTerm.lub(paramTy, retTy);
          } else return null;
        });
      }
      case NewTerm neu -> neu.struct();
      case ErrorTerm term -> ErrorTerm.typeOf(term.description());
      case ProjTerm proj -> {
        var sigmaRaw = tryPress(proj.of());
        if (!(sigmaRaw instanceof SigmaTerm sigma)) yield null;
        var index = proj.ix() - 1;
        var telescope = sigma.params();
        yield telescope.get(index).type().subst(ProjTerm.projSubst(proj.of(), index, telescope));
      }
      case MetaPatTerm metaPat -> metaPat.ref().type();
      case MetaLitTerm lit -> lit.type();
      case SortTerm sort -> sort.succ();
      case IntervalTerm interval -> SortTerm.ISet;
      case FormulaTerm end -> IntervalTerm.INSTANCE;
      case StringTerm str -> state.primFactory().getCall(PrimDef.ID.STRING);
      case IntegerTerm shaped -> shaped.type();
      case ListTerm shaped -> shaped.type();
      case PartialTyTerm ty -> synthesize(ty.type());
      case PartialTerm(var rhs, var par) -> new PartialTyTerm(par, rhs.restr());
      case PathTerm cube -> synthesize(cube.type());
      case MatchTerm match -> {
        // TODO: Should I normalize match.discriminant() before matching?
        var term = match.tryMatch();
        yield term.isDefined() ? synthesize(term.get()) : ErrorTerm.typeOf(match);
      }
      case CoeTerm coe -> PrimDef.familyLeftToRight(coe.type());
      case HCompTerm hComp -> throw new InternalException("TODO");
      case InTerm(var phi, var u) -> {
        var ty = press(u);
        yield state.primFactory().getCall(PrimDef.ID.SUB, ImmutableSeq.of(
            ty, phi, PartialTerm.from(phi, u, ty))
          .map(t -> new Arg<>(t, true)));
      }
      case OutTerm outS -> {
        var ty = tryPress(outS.of());
        if (ty instanceof PrimCall sub) yield sub.args().first().term();
        yield null;
      }
      case PAppTerm app -> {
        // v @ ui : A[ui/xi]
        var xi = app.cube().params();
        var ui = app.args().map(Arg::term);
        yield app.cube().type().subst(new Subst(xi, ui));
      }
      case PLamTerm(var params, var body) -> {
        var bud = tryPress(body);
        if (bud == null) yield null;
        yield new PathTerm(params, bud, new Partial.Const<>(body));
      }
      case AppTerm(var of, var arg) -> {
        var piRaw = tryPress(of);
        yield piRaw instanceof PiTerm pi ? pi.substBody(arg.term()) : null;
      }
      case default -> null;
    };
  }

  static <T> T unreachable(@NotNull Term preterm) {
    throw new AssertionError("Unexpected term: " + preterm);
  }

  private @NotNull Term whnf(Term x) {
    return x.normalize(state, NormalizeMode.WHNF);
  }
}
