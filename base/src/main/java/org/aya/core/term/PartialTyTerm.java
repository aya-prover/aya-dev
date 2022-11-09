// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.aya.core.visitor.AyaRestrSimplifier;
import org.aya.guest0x0.cubical.Restr;
import org.jetbrains.annotations.NotNull;

/** partial type */
public record PartialTyTerm(@NotNull Term type, @NotNull Restr<Term> restr) implements StableWHNF {
  public @NotNull PartialTyTerm normalizeRestr() {
    return new PartialTyTerm(type, AyaRestrSimplifier.INSTANCE.normalizeRestr(restr));
  }
}
