// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.def.Def;
import org.aya.core.def.PrimDef;
import org.aya.core.term.*;
import org.aya.core.visitor.DeltaExpander;
import org.aya.core.visitor.Subst;
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
  public @NotNull Term press(@NotNull Term preterm) {
    var synthesize = synthesize(preterm);
    assert synthesize != null : preterm.toDoc(AyaPrettierOptions.pretty()).debugRender();
    return whnf(synthesize);
  }

  public @Nullable Term synthesize(@NotNull Term preterm) {
    return switch (preterm) {
      case RefTerm term -> ctx.get(term.var());
      case ConCall conCall -> conCall.head().underlyingDataCall();
      case Callable.DefCall call -> Def.defResult(call.ref())
        .subst(DeltaExpander.buildSubst(Def.defTele(call.ref()), call.args()))
        .lift(call.ulift());
      // TODO: deal with type-only metas
      case MetaTerm hole -> {
        var result = hole.ref().result;
        if (result != null) yield result;
        var metas = state.metas();
        if (metas.containsKey(hole.ref())) {
          yield press(metas.get(hole.ref()));
        } else {
          throw new UnsupportedOperationException("TODO");
        }
      }
      case RefTerm.Field field -> Def.defType(field.ref());
      case FieldTerm access -> {
        var callRaw = press(preterm);
        if (!(callRaw instanceof StructCall call)) yield unreachable(callRaw);
        var field = access.ref();
        var subst = DeltaExpander.buildSubst(Def.defTele(field), access.fieldArgs())
          .add(DeltaExpander.buildSubst(Def.defTele(call.ref()), access.structArgs()));
        yield Def.defResult(field).subst(subst);
      }
      case SigmaTerm sigma -> {
        var univ = sigma.params().view()
          .map(param -> whnf(press(param.type())))
          .filterIsInstance(SortTerm.class)
          .toImmutableSeq();
        if (univ.sizeEquals(sigma.params().size())) {
          try {
            yield univ.reduce(SigmaTerm::max);
          } catch (IllegalArgumentException ignored) {
            yield ErrorTerm.typeOf(sigma);
          }
        } else {
          yield ErrorTerm.typeOf(sigma);
        }
      }
      case PiTerm pi -> {
        var paramTyRaw = whnf(press(pi.param().type()));
        var resultParam = new Term.Param(pi.param().ref(), whnf(pi.param().type()), pi.param().explicit());
        var t = new Synthesizer(state, ctx.deriveMap());
        yield t.ctx.with(resultParam, () -> {
          var retTyRaw = whnf(t.press(pi.body()));
          if (paramTyRaw instanceof SortTerm paramTy && retTyRaw instanceof SortTerm retTy) {
            try {
              return ExprTycker.sortPi(paramTy, retTy);
            } catch (IllegalArgumentException ignored) {
              return ErrorTerm.typeOf(pi);
            }
          } else {
            return ErrorTerm.typeOf(pi);
          }
        });
      }
      case NewTerm neu -> neu.struct();
      case ErrorTerm term -> ErrorTerm.typeOf(term.description());
      case ProjTerm proj -> {
        var sigmaRaw = press(proj.of());
        if (!(sigmaRaw instanceof SigmaTerm sigma)) yield ErrorTerm.typeOf(proj);
        var index = proj.ix() - 1;
        var telescope = sigma.params();
        yield telescope.get(index).type().subst(ProjTerm.projSubst(proj.of(), index, telescope));
      }
      case MetaPatTerm metaPat -> metaPat.ref().type();
      case MetaLitTerm lit -> lit.type();
      case SortTerm sort -> sort.succ();
      case IntervalTerm interval -> SortTerm.Type0;
      case FormulaTerm end -> IntervalTerm.INSTANCE;
      case StringTerm str -> state.primFactory().getCall(PrimDef.ID.STRING);
      case IntegerTerm shaped -> shaped.type();
      case ListTerm shaped -> shaped.type();
      case PartialTyTerm ty -> press(ty.type());
      case PartialTerm(var rhs, var par) -> new PartialTyTerm(par, rhs.restr());
      case PathTerm cube -> press(cube.type());
      case MatchTerm match -> {
        // TODO: Should I normalize match.discriminant() before matching?
        var term = match.tryMatch();
        yield term.isDefined() ? press(term.get()) : ErrorTerm.typeOf(match);
      }
      case CoeTerm coe -> PrimDef.familyLeftToRight(coe.type());
      case HCompTerm hComp -> throw new InternalException("TODO");
      case InOutTerm inS when inS.kind() == InOutTerm.Kind.In -> {
        var ty = press(inS.u());
        yield state.primFactory().getCall(PrimDef.ID.SUB, ImmutableSeq.of(
            ty, inS.phi(), PartialTerm.from(inS.phi(), inS.u(), ty))
          .map(t -> new Arg<>(t, true)));
      }
      case InOutTerm outS -> {
        var ty = press(outS.u());
        if (ty instanceof PrimCall sub) yield sub.args().first().term();
        else yield ErrorTerm.typeOf(outS);
      }
      case PAppTerm app -> {
        // v @ ui : A[ui/xi]
        var xi = app.cube().params();
        var ui = app.args().map(Arg::term);
        yield app.cube().type().subst(new Subst(xi, ui));
      }
      case PLamTerm lam -> {
        var bud = press(lam.body());
        yield new PathTerm(lam.params(), bud, new Partial.Const<>(bud));
      }
      case AppTerm app -> {
        var piRaw = press(app.of());
        yield piRaw instanceof PiTerm pi ? pi.substBody(app.arg().term()) : null;
      }
      case default -> null;
    };
  }

  private static <T> T unreachable(@NotNull Term preterm) {
    throw new AssertionError("Unexpected term: " + preterm);
  }

  private @NotNull Term whnf(Term x) {
    return x.normalize(state, NormalizeMode.WHNF);
  }

}
