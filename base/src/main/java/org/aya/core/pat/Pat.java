// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.pat;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.value.Ref;
import org.aya.api.core.CorePat;
import org.aya.api.distill.AyaDocile;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.util.Arg;
import org.aya.concrete.stmt.Decl;
import org.aya.core.Matching;
import org.aya.core.def.CtorDef;
import org.aya.core.def.PrimDef;
import org.aya.core.term.CallTerm;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.Substituter;
import org.aya.core.visitor.Zonker;
import org.aya.distill.BaseDistiller;
import org.aya.distill.CoreDistiller;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.env.LocalCtx;
import org.aya.tyck.env.SeqLocalCtx;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author kiva, ice1000
 */
@Debug.Renderer(text = "toTerm().toDoc(DistillerOptions.DEBUG).debugRender()")
public sealed interface Pat extends CorePat {
  @NotNull Term type();
  @Override default @NotNull Term toTerm() {
    return PatToTerm.INSTANCE.visit(this);
  }
  @Override default @NotNull Arg<Term> toArg() {
    return new Arg<>(toTerm(), explicit());
  }
  @Override default @NotNull Doc toDoc(@NotNull DistillerOptions options) {
    return new CoreDistiller(options).visitPat(this, BaseDistiller.Outer.Free);
  }
  @NotNull Pat rename(@NotNull Substituter.TermSubst subst, @NotNull LocalCtx localCtx, boolean explicit);
  @NotNull Pat zonk(@NotNull Zonker zonker);
  @NotNull Pat inline();
  void storeBindings(@NotNull LocalCtx localCtx);
  @NotNull Lhs toLhs();
  static @NotNull ImmutableSeq<Term.Param> extractTele(@NotNull SeqLike<Pat> pats) {
    var localCtx = new SeqLocalCtx();
    for (var pat : pats) pat.storeBindings(localCtx);
    return localCtx.extract();
  }

  record Bind(
    boolean explicit,
    @NotNull LocalVar bind,
    @NotNull Term type
  ) implements Pat {
    @Override public void storeBindings(@NotNull LocalCtx localCtx) {
      localCtx.put(bind, type);
    }

    @Override
    public @NotNull Pat rename(Substituter.@NotNull TermSubst subst, @NotNull LocalCtx localCtx, boolean explicit) {
      var newName = new LocalVar(bind.name(), bind.definition());
      var newBind = new Bind(explicit, newName, type.subst(subst));
      subst.addDirectly(bind, new RefTerm(newName));
      localCtx.put(newName, type);
      return newBind;
    }

    @Override public @NotNull Pat zonk(@NotNull Zonker zonker) {
      return new Bind(explicit, bind, zonker.zonk(type, bind.definition()));
    }

    @Override public @NotNull Pat inline() {
      return this;
    }

    @Override public @NotNull Lhs toLhs() {
      return new Lhs.Bind(explicit, bind, type);
    }
  }

  record Meta(
    boolean explicit,
    @NotNull Ref<Pat> solution,
    @NotNull LocalVar fakeBind,
    @NotNull Term type
  ) implements Pat {
    @Override public void storeBindings(@NotNull LocalCtx localCtx) {
      // Do nothing
      // This is safe because storeBindings is called only in extractTele which is
      // only used for constructor ownerTele extraction for simpler indexed types
    }

    @Override public @NotNull Pat zonk(@NotNull Zonker zonker) {
      throw new IllegalStateException("unreachable");
    }

    @Override public @NotNull Pat inline() {
      var value = solution.value;
      if (value == null) return solution.value = new Bind(explicit, fakeBind, type);
      else return value;
    }

    @Override
    public @NotNull Pat rename(Substituter.@NotNull TermSubst subst, @NotNull LocalCtx localCtx, boolean explicit) {
      throw new IllegalStateException("unreachable");
    }

    @Override public @NotNull Lhs toLhs() {
      throw new IllegalStateException();
    }
  }

  record Absurd(boolean explicit, @NotNull Term type) implements Pat {
    @Override public void storeBindings(@NotNull LocalCtx localCtx) {
      throw new IllegalStateException();
    }

    @Override
    public @NotNull Pat rename(Substituter.@NotNull TermSubst subst, @NotNull LocalCtx localCtx, boolean explicit) {
      throw new IllegalStateException();
    }

    @Override public @NotNull Pat zonk(@NotNull Zonker zonker) {
      return this;
    }

    @Override public @NotNull Pat inline() {
      return this;
    }

    @Override public @NotNull Lhs toLhs() {
      throw new IllegalStateException();
    }
  }

  record Tuple(
    boolean explicit,
    @NotNull ImmutableSeq<Pat> pats,
    @NotNull Term type
  ) implements Pat {
    @Override public void storeBindings(@NotNull LocalCtx localCtx) {
      pats.forEach(pat -> pat.storeBindings(localCtx));
    }

    @Override
    public @NotNull Pat rename(Substituter.@NotNull TermSubst subst, @NotNull LocalCtx localCtx, boolean explicit) {
      var params = pats.map(pat -> pat.rename(subst, localCtx, pat.explicit()));
      return new Tuple(explicit, params, type.subst(subst));
    }

    @Override public @NotNull Pat zonk(@NotNull Zonker zonker) {
      return new Tuple(explicit, pats.map(pat -> pat.zonk(zonker)), zonker.zonk(type, null));
    }

    @Override public @NotNull Pat inline() {
      return new Tuple(explicit, pats.map(Pat::inline), type);
    }

    @Override public @NotNull Lhs toLhs() {
      return new Lhs.Tuple(explicit, pats.map(Pat::toLhs));
    }
  }

  record Ctor(
    boolean explicit,
    @NotNull DefVar<CtorDef, Decl.DataCtor> ref,
    @NotNull ImmutableSeq<Pat> params,
    @NotNull CallTerm.Data type
  ) implements Pat {
    @Override public void storeBindings(@NotNull LocalCtx localCtx) {
      params.forEach(pat -> pat.storeBindings(localCtx));
    }

    @Override
    public @NotNull Pat rename(Substituter.@NotNull TermSubst subst, @NotNull LocalCtx localCtx, boolean explicit) {
      var params = this.params.map(pat -> pat.rename(subst, localCtx, pat.explicit()));
      return new Ctor(explicit, ref, params, (CallTerm.Data) type.subst(subst));
    }

    @Override public @NotNull Pat zonk(@NotNull Zonker zonker) {
      return new Ctor(explicit, ref, params.map(pat -> pat.zonk(zonker)),
        (CallTerm.Data) zonker.zonk(type, null));
      // The cast must succeed
    }

    @Override public @NotNull Pat inline() {
      return new Ctor(explicit, ref, params.map(Pat::inline), type);
    }

    @Override public @NotNull Lhs toLhs() {
      return new Lhs.Ctor(explicit, ref, params.map(Pat::toLhs), type);
    }
  }

  record Prim(
    boolean explicit,
    @NotNull DefVar<PrimDef, Decl.PrimDecl> ref,
    @NotNull Term type
  ) implements Pat {
    @Override public void storeBindings(@NotNull LocalCtx localCtx) {
      // Do nothing
    }

    @Override
    public @NotNull Pat rename(Substituter.@NotNull TermSubst subst, @NotNull LocalCtx localCtx, boolean explicit) {
      return this;
    }

    @Override public @NotNull Pat zonk(@Nullable Zonker zonker) {
      return this;
    }

    @Override public @NotNull Pat inline() {
      return this;
    }

    @Override public @NotNull Lhs toLhs() {
      return new Lhs.Prim(explicit, ref);
    }
  }

  /**
   * It's 'pre' because there are also impossible clauses, which are removed after tycking.
   *
   * @author ice1000
   */
  record Preclause<T extends AyaDocile>(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<Pat> patterns,
    @NotNull Option<T> expr
  ) implements AyaDocile {
    @Override public @NotNull Doc toDoc(@NotNull DistillerOptions options) {
      return new CoreDistiller(options).clause(patterns, expr);
    }

    public static @NotNull Preclause<Term> weaken(@NotNull Matching.Typed clause) {
      return new Preclause<>(clause.sourcePos(), clause.patterns(), Option.some(clause.body()));
    }

    public static @NotNull Option<Matching.@NotNull Typed> lift(@NotNull Preclause<Term> clause) {
      return clause.expr.map(term -> new Matching.Typed(clause.sourcePos, clause.patterns, term));
    }
  }
}
