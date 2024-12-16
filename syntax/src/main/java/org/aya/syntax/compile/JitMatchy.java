// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.compile;

import kala.collection.Seq;
import org.aya.syntax.core.def.MatchyLike;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public non-sealed abstract class JitMatchy extends JitDef implements MatchyLike {
  protected JitMatchy() {
    super(-1, new boolean[0], new String[0]);
  }

  /** @return null if stuck */
  public abstract @Nullable Term invoke(
    @NotNull Seq<@NotNull Term> captures,
    @NotNull Seq<@NotNull Term> args
  );
}
