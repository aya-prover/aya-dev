// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.compile;

import kala.collection.Seq;
import org.aya.generic.Modifier;
import org.aya.generic.stmt.Reducible;
import org.aya.syntax.core.def.FnDefLike;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public abstract non-sealed class JitFn extends JitDef implements FnDefLike, Reducible {
  public final int modifiers;

  protected JitFn(int telescopeSize, boolean[] telescopeLicit, String[] telescopeName, int modifiers) {
    super(telescopeSize, telescopeLicit, telescopeName);
    this.modifiers = modifiers;
  }

  /**
   * Unfold this function
   */
  @Override abstract public @NotNull Term
  invoke(@NotNull Supplier<Term> fallback, @NotNull Seq<@NotNull Term> args);
  @Override public boolean is(@NotNull Modifier mod) {
    return (modifiers & (1 << mod.ordinal())) != 0;
  }
}
