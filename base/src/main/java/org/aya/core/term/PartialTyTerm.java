// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.aya.core.visitor.AyaRestrSimplifier;
import org.aya.guest0x0.cubical.Restr;
import org.jetbrains.annotations.NotNull;

/** Type of partial elements. */
public record PartialTyTerm(@NotNull Term type, @NotNull Restr<Term> restr) implements StableWHNF, Formation {
  public @NotNull PartialTyTerm normalizeRestr() {
    var newRestr = AyaRestrSimplifier.INSTANCE.normalizeRestr(restr);
    if (newRestr == restr) return this;
    return new PartialTyTerm(type, newRestr);
  }
}
