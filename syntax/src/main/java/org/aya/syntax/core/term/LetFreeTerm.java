// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.function.IndexedFunction;
import org.aya.generic.TermVisitor;
import org.aya.syntax.core.Jdg;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

/// @param definedAs never get bind in the lifecycle of [LetFreeTerm]
@Closed
public record LetFreeTerm(@Override @NotNull LocalVar name, @Closed @NotNull Jdg definedAs) implements FreeTermLike {
  public @NotNull LetFreeTerm update(@Closed @NotNull Jdg definedAs) {
    return definedAs.wellTyped() == definedAs().wellTyped()
      ? this
      : new LetFreeTerm(name, definedAs);
  }

  @Override
  public @NotNull Term descent(@NotNull TermVisitor visitor) {
    return update(definedAs.map(visitor::term));
  }
}
