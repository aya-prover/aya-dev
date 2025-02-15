// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.compile;

import kala.collection.Seq;
import org.aya.syntax.core.def.MatchyLike;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.QName;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public non-sealed abstract class JitMatchy extends JitUnit implements MatchyLike {
  protected JitMatchy() {
    super();
  }

  public abstract @NotNull Term invoke(
    @NotNull UnaryOperator<Term> pre,
    @NotNull Seq<@NotNull Term> captures,
    @NotNull Seq<@NotNull Term> args
  );

  @Override
  public @NotNull QName qualifiedName() {
    return super.qualifiedName();
  }
}
