// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.term;

import asia.kala.collection.immutable.ImmutableSeq;
import asia.kala.control.Option;
import asia.kala.ref.OptionRef;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.ref.Var;
import org.mzi.generic.Arg;
import org.mzi.util.Decision;

/**
 * @author ice1000
 */
public record HoleTerm(
  @NotNull OptionRef<@NotNull Term> solution,
  @NotNull Var var,
  @NotNull ImmutableSeq<@NotNull ? extends @NotNull Arg<? extends Term>> args
) implements Term {
  public HoleTerm(@Nullable Term solution, @NotNull Var var,
                  @NotNull ImmutableSeq<@NotNull ? extends @NotNull Arg<? extends Term>> args) {
    this(new OptionRef<>(Option.of(solution)), var, args);
  }

  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitHole(this, p);
  }

  @Override public <P, Q, R> R accept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
    return visitor.visitHole(this, p, q);
  }

  @Contract(pure = true) @Override public @NotNull Decision whnf() {
    return Decision.MAYBE;
  }
}
