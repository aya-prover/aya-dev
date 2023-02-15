// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.aya.core.pat.Pat;
import org.aya.core.visitor.AyaRestrSimplifier;
import org.aya.guest0x0.cubical.Restr;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/** Type of partial elements. */
public record PartialTyTerm(@NotNull Term type, @NotNull Restr<Term> restr) implements StableWHNF, Formation {
  public @NotNull PartialTyTerm update(@NotNull Term type, @NotNull Restr<Term> restr) {
    return type == type() && restr == restr() ? this : new PartialTyTerm(type, restr);
  }

  @Override public @NotNull PartialTyTerm descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
    return update(f.apply(type), restr.map(f));
  }

  public @NotNull PartialTyTerm normalizeRestr() {
    var newRestr = AyaRestrSimplifier.INSTANCE.normalizeRestr(restr);
    if (newRestr == restr) return this;
    return new PartialTyTerm(type, newRestr);
  }
}
