// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.compile;

import kala.collection.Seq;
import org.aya.generic.Modifier;
import org.aya.syntax.core.def.FnDefLike;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.telescope.JitTele;
import org.jetbrains.annotations.NotNull;

public abstract non-sealed class JitFn extends JitTele implements FnDefLike {
  public final int modifiers;

  protected JitFn(int telescopeSize, boolean[] telescopeLicit, String[] telescopeName, int modifiers) {
    super(telescopeSize, telescopeLicit, telescopeName);
    this.modifiers = modifiers;
  }

  /**
   * Unfold this function
   */
  abstract public @NotNull Term invoke(@NotNull Seq<@NotNull Term> args);
  @Override public boolean is(@NotNull Modifier mod) {
    return (modifiers & (1 << mod.ordinal())) != 0;
  }
}
