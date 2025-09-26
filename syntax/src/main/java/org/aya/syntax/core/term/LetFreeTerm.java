// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.function.IndexedFunction;
import org.aya.syntax.core.Jdg;
import org.aya.syntax.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

public record LetFreeTerm(@Override @NotNull LocalVar name, @NotNull Jdg definedAs) implements FreeTermLike {
  public @NotNull LetFreeTerm update(@NotNull Jdg definedAs) {
    return definedAs.wellTyped() == definedAs().wellTyped()
      ? this
      : new LetFreeTerm(name, definedAs);
  }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(definedAs.map(t -> f.apply(0, t)));
  }
}
