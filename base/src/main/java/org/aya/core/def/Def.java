// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.def;

import org.aya.api.core.CoreDef;
import org.aya.api.ref.DefVar;
import org.aya.concrete.Signatured;
import org.aya.core.pretty.DefPrettier;
import org.aya.core.term.Term;
import org.aya.core.visitor.Substituter;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * @author ice1000
 */
public sealed interface Def extends CoreDef permits DataDef, DataDef.Ctor, FnDef, PrimDef, StructDef, StructDef.Field {
  static @NotNull ImmutableSeq<Term.Param> defContextTele(@NotNull DefVar<? extends Def, ? extends Signatured> defVar) {
    if (defVar.core != null) return defVar.core.contextTele();
      // guaranteed as this is already a core term
    else return Objects.requireNonNull(defVar.concrete.signature).contextParam;
  }
  static @NotNull ImmutableSeq<Term.Param> defTele(@NotNull DefVar<? extends Def, ? extends Signatured> defVar) {
    if (defVar.core != null) return defVar.core.telescope();
      // guaranteed as this is already a core term
    else return Objects.requireNonNull(defVar.concrete.signature).param;
  }
  static @NotNull Term defResult(@NotNull DefVar<? extends Def, ? extends Signatured> defVar) {
    if (defVar.core != null) return defVar.core.result();
      // guaranteed as this is already a core term
    else return Objects.requireNonNull(defVar.concrete.signature).result;
  }
  static @NotNull ImmutableSeq<Term.Param>
  substParams(@NotNull SeqLike<Term.@NotNull Param> param, Substituter.@NotNull TermSubst subst) {
    return Term.Param.subst(param.view().drop(1), subst);
  }

  @Override @NotNull Term result();
  @Override @NotNull DefVar<? extends Def, ? extends Signatured> ref();
  @Override @NotNull ImmutableSeq<Term.Param> contextTele();
  @Override @NotNull ImmutableSeq<Term.Param> telescope();

  default @NotNull ImmutableSeq<Term.Param> fullTelescope() {
    return contextTele().view().concat(telescope()).toImmutableSeq();
  }

  <P, R> R accept(@NotNull Visitor<P, R> visitor, P p);
  @Override default @NotNull Doc toDoc() {
    return accept(DefPrettier.INSTANCE, Unit.unit());
  }

  /**
   * @author re-xyr
   */
  interface Visitor<P, R> {
    R visitFn(@NotNull FnDef def, P p);
    R visitData(@NotNull DataDef def, P p);
    R visitCtor(DataDef.@NotNull Ctor def, P p);
    R visitStruct(@NotNull StructDef def, P p);
    R visitField(StructDef.@NotNull Field def, P p);
    R visitPrim(@NotNull PrimDef def, P p);
  }

  /**
   * Signature of a definition, used in concrete and tycking.
   *
   * @author ice1000
   */
  @Debug.Renderer(text = "toDoc().renderWithPageWidth(114514)")
  record Signature(
    @NotNull ImmutableSeq<Term.@NotNull Param> contextParam,
    @NotNull ImmutableSeq<Term.@NotNull Param> param,
    @NotNull Term result
  ) implements Docile {
    @Contract("_ -> new") public @NotNull Signature inst(@NotNull Term term) {
      var subst = new Substituter.TermSubst(param.first().ref(), term);
      return new Signature(Term.Param.subst(contextParam, subst), substParams(param, subst), result.subst(subst));
    }

    @Override public @NotNull Doc toDoc() {
      return Doc.hcat(Doc.join(Doc.plain(" "), param.stream().map(Term.Param::toDoc)),
        Doc.plain(" -> "), result.toDoc());
    }

    @Contract("_ -> new") public @NotNull Signature mapTerm(@NotNull Term term) {
      return new Signature(contextParam, param, term);
    }

    public @NotNull Signature subst(@NotNull Substituter.TermSubst subst) {
      return new Signature(Term.Param.subst(contextParam, subst), Term.Param.subst(param, subst), result.subst(subst));
    }
  }
}
