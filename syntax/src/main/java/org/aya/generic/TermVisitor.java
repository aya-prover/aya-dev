// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import org.aya.syntax.core.Closure;
import org.aya.syntax.core.term.Term;
import org.aya.util.Panic;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public interface TermVisitor {
  /// @return dblity inherits from {@param term}
  @NotNull Term term(@NotNull Term term);
  @NotNull Closure closure(@NotNull Closure closure);

  /// Construct a [TermVisitor] from {@param onTerm}, and panic when a [Closure] is met.
  static @NotNull TermVisitor ofTerm(@NotNull UnaryOperator<Term> onTerm) {
    return new TermVisitor() {
      @Override
      public @NotNull Term term(@NotNull Term term) {
        return onTerm.apply(term);
      }

      @Override
      public @NotNull Closure closure(@NotNull Closure closure) {
        return Panic.unreachable();
      }
    };
  }
}
