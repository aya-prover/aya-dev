// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core;

import org.aya.generic.term.ParamLike;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.LocalVar;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * A enriched {@link org.aya.syntax.core.term.Param}, which is usually used for prettier.
 */
public record RichParam(
  @Override @NotNull LocalVar ref,
  @Override @NotNull Term type,
  @Override boolean explicit
) implements ParamLike<Term> {
  public static @NotNull RichParam ofExplicit(@NotNull LocalVar ref, @NotNull Term type) {
    return new RichParam(ref, type, true);
  }

  @Contract("-> new")
  public @NotNull Param degenerate() {
    return new Param(ref.name(), type, explicit);
  }
}
