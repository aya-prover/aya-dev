// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.pat;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.core.CorePat;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.util.Arg;
import org.aya.concrete.stmt.Decl;
import org.aya.core.def.CtorDef;
import org.aya.core.def.PrimDef;
import org.aya.core.term.CallTerm;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.Substituter;
import org.aya.distill.BaseDistiller;
import org.aya.distill.CoreDistiller;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.env.LocalCtx;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

/**
 * Lhs is the Pat after type checking, so there shouldn't be too much info.
 * Lhs is mainly used at the evalutioin step, i.e. unfold/normalize
 *
 * @author tonfeiz
 */
@Debug.Renderer(text = "toTerm().toDoc(DistillerOptions.DEBUG).debugRender()")
public sealed interface Lhs extends CorePat {
  @Override default @NotNull Term toTerm() {
    return LhsToTerm.INSTANCE.visit(this);
  }
  @Override default @NotNull Doc toDoc(@NotNull DistillerOptions options) {
    return new CoreDistiller(options).visitLhs(this, BaseDistiller.Outer.Free);
  }
  @Override default @NotNull Arg<Term> toArg() {
    return new Arg<>(toTerm(), explicit());
  }
  @NotNull Lhs rename(@NotNull Substituter.TermSubst subst, @NotNull LocalCtx localCtx, boolean explicit);
  void storeBindings(@NotNull LocalCtx localCtx);
  boolean explicit();

  record Bind(
    boolean explicit,
    @NotNull LocalVar bind,
    @NotNull Term type
  ) implements Lhs {
    @Override public void storeBindings(@NotNull LocalCtx localCtx) {
      localCtx.put(bind, type);
    }

    @Override
    public @NotNull Lhs rename(Substituter.@NotNull TermSubst subst, @NotNull LocalCtx localCtx, boolean explicit) {
      var newName = new LocalVar(bind.name(), bind.definition());
      var newBind = new Bind(explicit, newName, type.subst(subst));
      subst.addDirectly(bind, new RefTerm(newName));
      localCtx.put(newName, type);
      return newBind;
    }
  }

  record Tuple(
    boolean explicit,
    @NotNull ImmutableSeq<Lhs> lhss
  ) implements Lhs {
    @Override public void storeBindings(@NotNull LocalCtx localCtx) {
      lhss.forEach(pat -> pat.storeBindings(localCtx));
    }

    @Override
    public @NotNull Lhs rename(Substituter.@NotNull TermSubst subst, @NotNull LocalCtx localCtx, boolean explicit) {
      var params = lhss.map(lhs -> lhs.rename(subst, localCtx, lhs.explicit()));
      return new Tuple(explicit, params);
    }
  }

  record Ctor(
    boolean explicit,
    @NotNull DefVar<CtorDef, Decl.DataCtor> ref,
    @NotNull ImmutableSeq<Lhs> params,
    @NotNull CallTerm.Data type
  ) implements Lhs {
    @Override public void storeBindings(@NotNull LocalCtx localCtx) {
      params.forEach(pat -> pat.storeBindings(localCtx));
    }

    @Override
    public @NotNull Lhs rename(Substituter.@NotNull TermSubst subst, @NotNull LocalCtx localCtx, boolean explicit) {
      var params = this.params.map(pat -> pat.rename(subst, localCtx, pat.explicit()));
      return new Ctor(explicit, ref, params, type);
    }
  }

  record Prim(
    boolean explicit,
    @NotNull DefVar<PrimDef, Decl.PrimDecl> ref
  ) implements Lhs {
    @Override public void storeBindings(@NotNull LocalCtx localCtx) {
      // Do nothing
    }

    @Override
    public @NotNull Lhs rename(Substituter.@NotNull TermSubst subst, @NotNull LocalCtx localCtx, boolean explicit) {
      return this;
    }
  }
}
