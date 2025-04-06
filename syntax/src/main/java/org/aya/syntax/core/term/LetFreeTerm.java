// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.function.IndexedFunction;
import org.aya.syntax.core.Jdg;
import org.aya.syntax.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public record LetFreeTerm(@Override @NotNull LocalVar name, @NotNull Term definedAs) implements FreeTermLike {
  public @NotNull LetFreeTerm update(@NotNull Term definedAs) {
    return definedAs == definedAs()
      ? this
      : new LetFreeTerm(name, definedAs);
  }

  // TODO: is it good to 'descent' a [LetFreeTerm]? I think the proper way to descent a [LetFreeTerm] is descent a [LetTerm].
  @Override
  public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(f.apply(0, definedAs));
  }
}
