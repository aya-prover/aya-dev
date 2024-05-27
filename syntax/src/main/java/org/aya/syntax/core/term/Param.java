// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.collection.SeqView;
import org.aya.generic.AyaDocile;
import org.aya.prettier.BasePrettier;
import org.aya.prettier.CorePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.UnaryOperator;

public record Param(@NotNull String name, @NotNull Term type, boolean explicit) implements AyaDocile {
  public static @NotNull SeqView<Param> substTele(SeqView<Param> tele, SeqView<Term> subst) {
    return tele.mapIndexed((idx, p) -> p.descent(ty -> ty.replaceTeleFrom(idx, subst)));
  }

  public boolean nameEq(@Nullable String otherName) { return name.equals(otherName); }
  // public @NotNull Arg<Term> toArg() { return new Arg<>(type, explicit); }
  public @NotNull Pat toFreshPat() { return new Pat.Bind(LocalVar.generate(name), type); }
  public @NotNull FreeTerm toFreshTerm() { return new FreeTerm(name); }
  public @NotNull Param implicitize() { return new Param(name, type, false); }
  public @NotNull Param explicitize() { return new Param(name, type, true); }

  // public @NotNull Param bindAt(LocalVar ref, int i) { return this.descent(t -> t.bindAt(ref, i)); }
  public Param instTele(SeqView<Term> terms) { return update(type.instantiateTele(terms)); }

  public @NotNull Param update(@NotNull Term type) {
    return type == this.type ? this : new Param(name, type, explicit);
  }

  public @NotNull Param descent(@NotNull UnaryOperator<Term> mapper) {
    return update(mapper.apply(type));
  }

  @Override
  public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
    return new CorePrettier(options).visitParam(this, BasePrettier.Outer.Free);
  }
}
