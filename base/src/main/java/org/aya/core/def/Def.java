// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.def;

import org.aya.api.core.def.CoreDef;
import org.aya.api.ref.DefVar;
import org.aya.concrete.Signatured;
import org.aya.core.pretty.DefPrettier;
import org.aya.core.term.Term;
import org.aya.core.visitor.Substituter;
import org.aya.pretty.doc.Doc;
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
public interface Def extends CoreDef {
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

  @NotNull Term result();
  @Override @NotNull DefVar<? extends Def, ? extends Signatured> ref();
  @NotNull ImmutableSeq<Term.Param> contextTele();
  @NotNull ImmutableSeq<Term.Param> telescope();

  <P, R> R accept(@NotNull Visitor<P, R> visitor, P p);
  default @NotNull Doc toDoc() {
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
  ) {
    @Contract("_ -> new") public @NotNull Signature inst(@NotNull Term term) {
      var subst = new Substituter.TermSubst(param.first().ref(), term);
      if (contextParam.isEmpty()) return new Signature(contextParam, substParams(param, subst), result.subst(subst));
      else return new Signature(substParams(contextParam, subst), Term.Param.subst(param, subst), result.subst(subst));
    }

    public @NotNull Doc toDoc() {
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
