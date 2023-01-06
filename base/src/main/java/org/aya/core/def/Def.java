// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.TeleDecl;
import org.aya.core.term.PiTerm;
import org.aya.core.term.Term;
import org.aya.generic.AyaDocile;
import org.aya.prettier.CorePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.ref.DefVar;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * @author ice1000
 */
public sealed interface Def extends AyaDocile, GenericDef permits SubLevelDef, TopLevelDef {
  static @NotNull Term defType(@NotNull DefVar<? extends Def, ? extends Decl.Telescopic<?>> defVar) {
    return PiTerm.make(defTele(defVar), defResult(defVar));
  }

  static @NotNull ImmutableSeq<Term.Param> defTele(@NotNull DefVar<? extends Def, ? extends Decl.Telescopic<?>> defVar) {
    if (defVar.core != null) return defVar.core.telescope();
      // guaranteed as this is already a core term
    else return Objects.requireNonNull(defVar.concrete.signature()).param;
  }
  static @NotNull Seq<CtorDef> dataBody(@NotNull DefVar<? extends DataDef, ? extends TeleDecl.DataDecl> defVar) {
    if (defVar.core != null) return defVar.core.body;
      // guaranteed as this is already a core term
    else return defVar.concrete.checkedBody;
  }
  @SuppressWarnings("unchecked") @Contract(pure = true)
  static <T extends Term> @NotNull T
  defResult(@NotNull DefVar<? extends Def, ? extends Decl.Telescopic<? extends T>> defVar) {
    if (defVar.core != null) return (T) defVar.core.result();
      // guaranteed as this is already a core term
    else return Objects.requireNonNull(defVar.concrete.signature()).result;
  }

  @Override @NotNull DefVar<? extends Def, ? extends Decl> ref();
  @NotNull ImmutableSeq<Term.Param> telescope();

  @Override default @NotNull Doc toDoc(@NotNull PrettierOptions options) {
    return new CorePrettier(options).def(this);
  }

  /**
   * Signature of a definition, used in concrete and tycking.
   *
   * @author ice1000
   */
  record Signature<T extends Term>(
    @NotNull ImmutableSeq<Term.@NotNull Param> param,
    @NotNull T result
  ) implements AyaDocile {
    @Override public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.sep(param.view().map(p -> p.toDoc(options))), Doc.symbol("->"), result.toDoc(options));
    }
  }
}
