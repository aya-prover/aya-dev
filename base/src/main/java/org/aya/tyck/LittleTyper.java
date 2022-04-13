// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.tuple.Unit;
import org.aya.concrete.stmt.Decl;
import org.aya.core.def.Def;
import org.aya.core.term.*;
import org.aya.core.visitor.Subst;
import org.aya.core.visitor.Unfolder;
import org.aya.generic.Constants;
import org.aya.generic.util.NormalizeMode;
import org.aya.ref.DefVar;
import org.aya.tyck.env.LocalCtx;
import org.jetbrains.annotations.NotNull;

/**
 * Similar to <code>GetTypeVisitor</code> in Arend.
 *
 * @author ice1000
 */
public record LittleTyper(@NotNull TyckState state, @NotNull LocalCtx localCtx) implements Term.Visitor<Unit, @NotNull Term> {
  @Override public @NotNull Term visitRef(@NotNull RefTerm term, Unit unit) {
    return localCtx.get(term.var());
  }

  @Override public @NotNull Term visitLam(IntroTerm.@NotNull Lambda term, Unit unit) {
    return new FormTerm.Pi(term.param(), term.body().accept(this, unit));
  }

  @Override public @NotNull Term visitPi(FormTerm.@NotNull Pi term, Unit unit) {
    var paramTyRaw = term.param().type().accept(this, Unit.unit()).normalize(state, NormalizeMode.WHNF);
    var retTyRaw = term.body().accept(this, Unit.unit()).normalize(state, NormalizeMode.WHNF);
    if (paramTyRaw instanceof FormTerm.Univ paramTy && retTyRaw instanceof FormTerm.Univ retTy)
      return new FormTerm.Univ(Math.max(paramTy.lift(), retTy.lift()));
    else return ErrorTerm.typeOf(term);
  }

  @Override public @NotNull Term visitError(@NotNull ErrorTerm term, Unit unit) {
    return ErrorTerm.typeOf(term);
  }

  @Override public @NotNull Term visitMetaPat(RefTerm.@NotNull MetaPat metaPat, Unit unit) {
    return metaPat.ref().type();
  }

  @Override public @NotNull Term visitSigma(FormTerm.@NotNull Sigma term, Unit unit) {
    var univ = term.params().view()
      .map(param -> param.type()
        .accept(this, Unit.unit()).normalize(state, NormalizeMode.WHNF))
      .filterIsInstance(FormTerm.Univ.class)
      .toImmutableSeq();
    if (univ.sizeEquals(term.params().size()))
      return new FormTerm.Univ(univ.view().map(FormTerm.Univ::lift).max());
    else return ErrorTerm.typeOf(term);
  }

  @Override public @NotNull Term visitUniv(FormTerm.@NotNull Univ term, Unit unit) {
    return new FormTerm.Univ(term.lift() + 1);
  }

  @Override public @NotNull Term visitApp(ElimTerm.@NotNull App term, Unit unit) {
    var piRaw = term.of().accept(this, unit).normalize(state, NormalizeMode.WHNF);
    return piRaw instanceof FormTerm.Pi pi ? pi.substBody(term.arg().term()) : ErrorTerm.typeOf(term);
  }

  @Override public @NotNull Term visitFnCall(@NotNull CallTerm.Fn fnCall, Unit unit) {
    return defCall(fnCall.ref(), fnCall.ulift());
  }

  @Override public @NotNull Term visitDataCall(@NotNull CallTerm.Data dataCall, Unit unit) {
    return defCall(dataCall.ref(), dataCall.ulift());
  }

  @Override public @NotNull Term visitConCall(@NotNull CallTerm.Con conCall, Unit unit) {
    return defCall(conCall.head().dataRef(), conCall.ulift());
  }

  @Override public @NotNull Term visitStructCall(@NotNull CallTerm.Struct structCall, Unit unit) {
    return defCall(structCall.ref(), structCall.ulift());
  }

  @NotNull
  private Term defCall(DefVar<? extends Def, ? extends Decl> ref, int ulift) {
    return Def.defResult(ref).subst(Subst.EMPTY, ulift);
  }

  @Override public @NotNull Term visitPrimCall(CallTerm.@NotNull Prim prim, Unit unit) {
    return defCall(prim.ref(), prim.ulift());
  }

  @Override public @NotNull Term visitTup(IntroTerm.@NotNull Tuple term, Unit unit) {
    return new FormTerm.Sigma(term.items().map(item ->
      new Term.Param(Constants.anonymous(), item.accept(this, Unit.unit()), true)));
  }

  @Override public @NotNull Term visitNew(IntroTerm.@NotNull New newTerm, Unit unit) {
    return newTerm.struct();
  }

  @Override public @NotNull Term visitProj(ElimTerm.@NotNull Proj term, Unit unit) {
    var sigmaRaw = term.of().accept(this, unit).normalize(state, NormalizeMode.WHNF);
    if (!(sigmaRaw instanceof FormTerm.Sigma sigma)) return ErrorTerm.typeOf(term);
    var index = term.ix() - 1;
    var telescope = sigma.params();
    return telescope.get(index).type()
      .subst(ElimTerm.Proj.projSubst(term.of(), index, telescope));
  }

  @Override public @NotNull Term visitAccess(CallTerm.@NotNull Access term, Unit unit) {
    var callRaw = term.of().accept(this, unit).normalize(state, NormalizeMode.WHNF);
    if (!(callRaw instanceof CallTerm.Struct call)) return ErrorTerm.typeOf(term);
    var core = term.ref().core;
    var subst = Unfolder.buildSubst(core.telescope(), term.fieldArgs())
      .add(Unfolder.buildSubst(call.ref().core.telescope(), term.structArgs()));
    return core.result().subst(subst);
  }

  @Override public @NotNull Term visitHole(CallTerm.@NotNull Hole term, Unit unit) {
    var result = term.ref().result;
    if (result == null) return ErrorTerm.typeOf(term);
    return result;
  }

  @Override
  public @NotNull Term visitFieldRef(@NotNull RefTerm.Field term, Unit unit) {
    return Def.defType(term.ref());
  }
}
