// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import org.aya.core.def.Def;
import org.aya.core.def.PrimDef;
import org.aya.core.term.*;
import org.aya.core.visitor.DeltaExpander;
import org.aya.core.visitor.Subst;
import org.aya.generic.Arg;
import org.aya.generic.Constants;
import org.aya.generic.util.InternalException;
import org.aya.generic.util.NormalizeMode;
import org.aya.guest0x0.cubical.Partial;
import org.aya.tyck.env.LocalCtx;
import org.jetbrains.annotations.NotNull;

/**
 * Similar to <code>GetTypeVisitor</code> in Arend.
 *
 * @author ice1000
 */
public record LittleTyper(@NotNull TyckState state, @NotNull LocalCtx localCtx) {
  public @NotNull Term term(@NotNull Term preterm) {
    return switch (preterm) {
      case RefTerm term -> localCtx.get(term.var());
      case CallTerm.Con conCall -> conCall.head().underlyingDataCall();
      case CallTerm.DefCall call -> defCall(call);
      case CallTerm.Hole hole -> {
        var result = hole.ref().result;
        yield result == null ? ErrorTerm.typeOf(hole) : result;
      }
      case ErrorTerm term -> ErrorTerm.typeOf(term);
      case RefTerm.Field field -> Def.defType(field.ref());
      case CallTerm.Access access -> {
        var callRaw = whnf(term(access.of()));
        if (!(callRaw instanceof CallTerm.Struct call)) yield ErrorTerm.typeOf(access);
        var core = access.ref().core;
        var subst = DeltaExpander.buildSubst(core.telescope(), access.fieldArgs())
          .add(DeltaExpander.buildSubst(call.ref().core.telescope(), access.structArgs()));
        yield core.result().subst(subst);
      }
      case FormTerm.Sigma sigma -> {
        var univ = sigma.params().view()
          .map(param -> whnf(term(param.type())))
          .filterIsInstance(FormTerm.Sort.class)
          .toImmutableSeq();
        if (univ.sizeEquals(sigma.params().size())) {
          try {
            yield univ.reduce(ExprTycker::calculateSigma);
          } catch (IllegalArgumentException ignored) {
            yield ErrorTerm.typeOf(sigma);
          }
        } else {
          yield ErrorTerm.typeOf(sigma);
        }
      }
      case IntroTerm.Lambda lambda -> new FormTerm.Pi(lambda.param(), term(lambda.body()));
      case ElimTerm.Proj proj -> {
        var sigmaRaw = whnf(term(proj.of()));
        if (!(sigmaRaw instanceof FormTerm.Sigma sigma)) yield ErrorTerm.typeOf(proj);
        var index = proj.ix() - 1;
        var telescope = sigma.params();
        yield telescope.get(index).type()
          .subst(ElimTerm.Proj.projSubst(proj.of(), index, telescope));
      }
      case IntroTerm.New neu -> neu.struct();
      case IntroTerm.Tuple tuple -> new FormTerm.Sigma(tuple.items().map(item ->
        new Term.Param(Constants.anonymous(), term(item), true)));
      case RefTerm.MetaPat metaPat -> metaPat.ref().type();
      case FormTerm.Pi pi -> {
        var paramTyRaw = whnf(term(pi.param().type()));
        var resultParam = new Term.Param(pi.param().ref(), whnf(pi.param().type()), pi.param().explicit());
        var t = new LittleTyper(state, localCtx.deriveMap());
        yield t.localCtx.with(resultParam, () -> {
          var retTyRaw = whnf(t.term(pi.body()));
          if (paramTyRaw instanceof FormTerm.Sort paramTy && retTyRaw instanceof FormTerm.Sort retTy) {
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
      case ElimTerm.App app -> {
        var piRaw = whnf(term(app.of()));
        yield piRaw instanceof FormTerm.Pi pi ? pi.substBody(app.arg().term()) : ErrorTerm.typeOf(app);
      }
      case FormTerm.Sort sort -> sort.succ();
      case PrimTerm.Interval interval -> FormTerm.Sort.Type0;
      case PrimTerm.Mula end -> PrimTerm.Interval.INSTANCE;
      case PrimTerm.Str str -> state.primFactory().getCall(PrimDef.ID.STRING);
      case LitTerm.ShapedInt shaped -> shaped.type();
      case LitTerm.ShapedList shaped -> shaped.type();
      case FormTerm.PartTy ty -> term(ty.type());
      case IntroTerm.PartEl el -> new FormTerm.PartTy(el.rhsType(), el.partial().restr());
      case FormTerm.Path(var cube) -> term(cube.type());
      case IntroTerm.PathLam lam -> new FormTerm.Path(new FormTerm.Cube(
        lam.params(),
        term(lam.body()),
        new Partial.Const<>(term(lam.body()))
      ));
      case ElimTerm.PathApp app -> {
        // v @ ui : A[ui/xi]
        var xi = app.cube().params();
        var ui = app.args().map(Arg::term);
        yield app.cube().type().subst(new Subst(xi, ui));
      }
      case PrimTerm.Coe coe -> PrimDef.familyLeftToRight(coe.type());
      case PrimTerm.HComp hComp -> throw new InternalException("TODO");
      case ErasedTerm erased -> erased.type();
    };
  }

  private @NotNull Term whnf(Term x) {
    return x.normalize(state, NormalizeMode.WHNF);
  }

  private @NotNull Term defCall(@NotNull CallTerm.DefCall call) {
    return Def.defResult(call.ref())
      .subst(DeltaExpander.buildSubst(Def.defTele(call.ref()), call.args()))
      .lift(call.ulift());
  }
}
