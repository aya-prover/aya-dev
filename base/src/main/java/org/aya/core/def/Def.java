// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.def;

import org.aya.api.core.def.CoreDef;
import org.aya.api.ref.DefVar;
import org.aya.concrete.Signatured;
import org.aya.core.term.Term;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.SeqLike;
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

  <P, R> R accept(Visitor<P, R> visitor, P p);

  /**
   * @author re-xyr
   */
  interface Visitor<P, R> {
    R visitFn(@NotNull FnDef def, P p);
    R visitData(@NotNull DataDef def, P p);
    R visitCtor(DataDef.@NotNull Ctor def, P p);
  }

  record Signature(
    @NotNull Seq<Term.@NotNull Param> param,
    @NotNull Term result
  ) {
  }
}
