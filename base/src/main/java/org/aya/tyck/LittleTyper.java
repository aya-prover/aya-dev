// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import org.aya.core.def.Def;
import org.aya.core.def.PrimDef;
import org.aya.core.term.*;
import org.aya.core.visitor.Expander;
import org.aya.core.visitor.Subst;
import org.aya.generic.Constants;
import org.aya.generic.util.NormalizeMode;
import org.aya.guest0x0.cubical.CofThy;
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
      case CallTerm.Data dataCall -> Def.defResult(dataCall.ref()).lift(dataCall.ulift());
      case CallTerm.Struct structCall -> Def.defResult(structCall.ref()).lift(structCall.ulift());
      case CallTerm.Hole hole -> {
        var result = hole.ref().result;
        yield result == null ? ErrorTerm.typeOf(hole) : result;
      }
      case ErrorTerm term -> ErrorTerm.typeOf(term);
      case RefTerm.Field field -> Def.defType(field.ref());
      case CallTerm.Access access -> {
        var callRaw = term(access.of()).normalize(state, NormalizeMode.WHNF);
        if (!(callRaw instanceof CallTerm.Struct call)) yield ErrorTerm.typeOf(access);
        var core = access.ref().core;
        var subst = Expander.buildSubst(core.telescope(), access.fieldArgs())
          .add(Expander.buildSubst(call.ref().core.telescope(), access.structArgs()));
        yield core.result().subst(subst);
      }
      case FormTerm.Sigma sigma -> {
        var univ = sigma.params().view()
          .map(param -> term(param.type()).normalize(state, NormalizeMode.WHNF))
          .filterIsInstance(FormTerm.Univ.class)
          .toImmutableSeq();
        if (univ.sizeEquals(sigma.params().size()))
          yield new FormTerm.Univ(univ.view().map(FormTerm.Univ::lift).max());
        else yield ErrorTerm.typeOf(sigma);
      }
      case IntroTerm.Lambda lambda -> new FormTerm.Pi(lambda.param(), term(lambda.body()));
      case ElimTerm.Proj proj -> {
        var sigmaRaw = term(proj.of()).normalize(state, NormalizeMode.WHNF);
        if (!(sigmaRaw instanceof FormTerm.Sigma sigma)) yield ErrorTerm.typeOf(proj);
        var index = proj.ix() - 1;
        var telescope = sigma.params();
        yield telescope.get(index).type()
          .subst(ElimTerm.Proj.projSubst(proj.of(), index, telescope));
      }
      case IntroTerm.New neu -> neu.struct();
      case IntroTerm.Tuple tuple -> new FormTerm.Sigma(tuple.items().map(item ->
        new Term.Param(Constants.anonymous(), term(item), true)));
      case CallTerm.Con conCall -> Def.defResult(conCall.head().dataRef()).lift(conCall.ulift());
      case CallTerm.Prim prim -> Def.defResult(prim.ref()).lift(prim.ulift());
      case CallTerm.Fn fnCall -> Def.defResult(fnCall.ref()).lift(fnCall.ulift());
      case RefTerm.MetaPat metaPat -> metaPat.ref().type();
      case FormTerm.Pi pi -> {
        var paramTyRaw = term(pi.param().type()).normalize(state, NormalizeMode.WHNF);
        var retTyRaw = term(pi.body()).normalize(state, NormalizeMode.WHNF);
        if (paramTyRaw instanceof FormTerm.Univ paramTy && retTyRaw instanceof FormTerm.Univ retTy)
          yield new FormTerm.Univ(Math.max(paramTy.lift(), retTy.lift()));
        else yield ErrorTerm.typeOf(pi);
      }
      case ElimTerm.App app -> {
        var piRaw = term(app.of()).normalize(state, NormalizeMode.WHNF);
        yield piRaw instanceof FormTerm.Pi pi ? pi.substBody(app.arg().term()) : ErrorTerm.typeOf(app);
      }
      case FormTerm.Univ univ -> new FormTerm.Univ(univ.lift() + 1);
      case FormTerm.Interval interval -> FormTerm.Univ.ZERO;
      case PrimTerm.Mula end -> FormTerm.Interval.INSTANCE;
      case PrimTerm.Str str -> state.primFactory().getCall(PrimDef.ID.STR);
      case LitTerm.ShapedInt shaped -> shaped.type();
      case FormTerm.PartTy ty -> FormTerm.Univ.ZERO;
      case IntroTerm.HappyPartEl el -> {
        var first = el.clauses().firstOption();
        var A = first.flatMap(clause -> CofThy.vdash(clause.cof(), new Subst(), subst ->
          term(subst.term(state, clause.u()))));
        if (A.isDefined() && A.get() == null) yield ErrorTerm.typeOf(el);
        var restr = el.restr();
        yield new FormTerm.PartTy(A.get(), restr);
      }
      case IntroTerm.SadPartEl el -> new FormTerm.PartTy(term(el), el.restr());
    };
  }
}
