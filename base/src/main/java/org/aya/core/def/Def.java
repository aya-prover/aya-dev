// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.def;

import org.aya.api.core.def.CoreDef;
import org.aya.api.ref.DefVar;
import org.aya.concrete.Signatured;
import org.aya.core.term.Term;
import org.aya.core.visitor.Substituter;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * @author ice1000
 */
public interface Def extends CoreDef {
  static @NotNull SeqLike<Term.Param> defTele(@NotNull DefVar<? extends Def, ? extends Signatured> defVar) {
    if (defVar.core != null) return defVar.core.telescope();
      // guaranteed as this is already a core term
    else return Objects.requireNonNull(defVar.concrete.signature).param;
  }
  static @NotNull Term defResult(@NotNull DefVar<? extends Def, ? extends Signatured> defVar) {
    if (defVar.core != null) return defVar.core.result();
      // guaranteed as this is already a core term
    else return Objects.requireNonNull(defVar.concrete.signature).result;
  }

  @NotNull Term result();
  @Override @NotNull DefVar<? extends Def, ? extends Signatured> ref();
  @NotNull SeqLike<Term.Param> telescope();

  <P, R> R accept(@NotNull Visitor<P, R> visitor, P p);

  /**
   * @author re-xyr
   */
  interface Visitor<P, R> {
    R visitFn(@NotNull FnDef def, P p);
    R visitData(@NotNull DataDef def, P p);
    R visitCtor(DataDef.@NotNull Ctor def, P p);
  }

  /**
   * Signature of a definition, used in concrete and tycking.
   *
   * @author ice1000
   */
  record Signature(
    @NotNull ImmutableSeq<Term.@NotNull Param> param,
    @NotNull Term result
  ) {
    @Contract("_ -> new") public @NotNull Signature inst(@NotNull Term term) {
      var subst = new Substituter.TermSubst(param.first().ref(), term);
      var params = param.view().drop(1).map(p -> p.subst(subst));
      return new Signature(params.toImmutableSeq(), result.subst(subst));
    }

    @Contract("_ -> new") public @NotNull Signature mapTerm(@NotNull Term term) {
      return new Signature(param, term);
    }

    public @NotNull Signature subst(@NotNull Substituter.TermSubst subst) {
      return new Signature(param.map(p -> p.subst(subst)), result.subst(subst));
    }
  }
}
