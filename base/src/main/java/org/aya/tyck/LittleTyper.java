// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import org.aya.api.ref.DefVar;
import org.aya.concrete.Decl;
import org.aya.core.def.Def;
import org.aya.core.sort.Sort;
import org.aya.core.term.*;
import org.aya.core.visitor.Substituter;
import org.aya.core.visitor.Unfolder;
import org.aya.generic.Level;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Similar to <code>GetTypeVisitor</code> in Arend.
 *
 * @author ice1000
 */
public record LittleTyper(@NotNull ImmutableSeq<Term.Param> context) implements Term.Visitor<Unit, Term> {
  @Override public Term visitRef(@NotNull RefTerm term, Unit unit) {
    return context.find(param -> param.ref() == term.var()).map(Term.Param::type).getOrNull();
  }

  @Override public Term visitLam(IntroTerm.@NotNull Lambda term, Unit unit) {
    return new FormTerm.Pi(false, term.param(), term.body().accept(this, unit));
  }

  @Override public Term visitPi(FormTerm.@NotNull Pi term, Unit unit) {
    // var paramTy = (FormTerm.Univ) term.param().type().accept(this, unit);
    // var retTy = (FormTerm.Univ) term.body().accept(this, unit);
    // return new FormTerm.Univ(paramTy.sort().max(retTy.sort()));
    return null;
  }

  @Override public Term visitSigma(FormTerm.@NotNull Sigma term, Unit unit) {
    throw new UnsupportedOperationException("TODO");
  }

  @Override public Term visitUniv(FormTerm.@NotNull Univ term, Unit unit) {
    return new FormTerm.Univ(term.sort().succ(1));
  }

  @Override public Term visitApp(ElimTerm.@NotNull App term, Unit unit) {
    return null;
  }

  @Override public Term visitFnCall(@NotNull CallTerm.Fn fnCall, Unit unit) {
    return defCall(fnCall.ref(), fnCall.sortArgs());
  }

  @Override public Term visitDataCall(@NotNull CallTerm.Data dataCall, Unit unit) {
    return defCall(dataCall.ref(), dataCall.sortArgs());
  }

  @Override public Term visitConCall(@NotNull CallTerm.Con conCall, Unit unit) {
    return defCall(conCall.head().dataRef(), conCall.sortArgs());
  }

  @Override public Term visitStructCall(@NotNull CallTerm.Struct structCall, Unit unit) {
    return defCall(structCall.ref(), structCall.sortArgs());
  }

  @NotNull
  private Term defCall(DefVar<? extends Def, ? extends Decl> ref, ImmutableSeq<@NotNull Level<Sort.LvlVar>> sortArgs) {
    var levels = Def.defLevels(ref);
    return Def.defResult(ref).subst(Substituter.TermSubst.EMPTY, Unfolder.buildSubst(levels, sortArgs));
  }

  @Override public Term visitPrimCall(CallTerm.@NotNull Prim prim, Unit unit) {
    return defCall(prim.ref(), prim.sortArgs());
  }

  @Override public Term visitTup(IntroTerm.@NotNull Tuple term, Unit unit) {
    return null;
  }

  @Override public Term visitNew(IntroTerm.@NotNull New newTerm, Unit unit) {
    return null;
  }

  @Override public Term visitProj(ElimTerm.@NotNull Proj term, Unit unit) {
    return null;
  }

  @Override public Term visitAccess(CallTerm.@NotNull Access term, Unit unit) {
    return null;
  }

  @Override public Term visitHole(CallTerm.@NotNull Hole term, Unit unit) {
    return null;
  }
}
