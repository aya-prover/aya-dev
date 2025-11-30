// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import org.aya.syntax.core.Closure;
import org.aya.syntax.core.annotation.Bound;
import org.aya.syntax.core.term.Term;
import org.aya.util.Panic;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public interface TermVisitor {
  interface ExpectTerm extends TermVisitor {
    @Override default @NotNull Closure closure(@NotNull Closure closure) { return Panic.unreachable(); }
  }

  interface Traverse extends TermVisitor {
    @Override default @NotNull Closure closure(@NotNull Closure closure) { return closure.descent(this::term); }
  }

  /// Called when [Term#descent] a sub-[Term].
  /// This method must keep type former (in Java level) unless {@param term} is [org.aya.syntax.core.term.marker.BetaRedex].
  /// @return dblity inherits from {@param term}
  @NotNull Term term(@NotNull Term term);

  /// Called when [Term#descent] a sub-[Closure]
  @NotNull Closure closure(@NotNull Closure closure);

  /// Construct a [TermVisitor] from {@param onTerm}, and panic when a [Closure] is met.
  static @NotNull TermVisitor expectTerm(@NotNull UnaryOperator<Term> onTerm) {
    return (ExpectTerm) onTerm::apply;
  }

  /// Just traverse, make sure you will keep the dbi-level of [Term], see [Bound]
  static @NotNull TermVisitor of(@NotNull UnaryOperator<@Bound Term> f) {
    return (Traverse) f::apply;
  }
}
