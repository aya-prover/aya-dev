// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.aya.guest0x0.cubical.Restr;
import org.jetbrains.annotations.NotNull;

/** partial type */
public record PartialTyTerm(@NotNull Term type, @NotNull Restr<Term> restr) implements Term {
  public @NotNull PartialTyTerm normalizeRestr() {
    return new PartialTyTerm(type(), restr().normalize());
  }
}
