// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import kala.function.IndexedFunction;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.annotation.Bound;
import org.aya.syntax.core.term.AppTerm;
import org.aya.syntax.core.term.LamTerm;
import org.aya.syntax.core.term.LocalTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.marker.BindingIntro;
import org.aya.util.Panic;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public interface TermVisitor {
  interface ExpectTerm extends TermVisitor {
    @Override
    default @NotNull Closure closure(@NotNull Closure closure) {
      return Panic.unreachable();
    }
  }

  interface Traverse extends TermVisitor {
    @Override
    default @NotNull Closure closure(@NotNull Closure closure) {
      return closure.descent(this::term);
    }
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

  /// > This function is kept for commemorating. You should **NOT** use this factory function.
  ///
  /// For example, a {@link LamTerm}:
  /// ```
  ///     Γ, a : A ⊢ b : B
  /// --------------------------
  /// Γ ⊢ fn (a : A) => (b : B)
  ///```
  /// `f` will apply to `b`, but the context of `b`: `Γ, a : A` has a new binding,
  /// therefore the implementation should be `f.apply(1, b)`.
  /// In the other hand, a [AppTerm]:
  /// ```
  /// Γ ⊢ g : A → B   Γ ⊢ a : A
  /// --------------------------
  ///        Γ ⊢ g a : B
  ///```
  /// `f` will apply to both `g` and `a`, but the context of them have no extra binding,
  /// so the implementation should be `f.apply(0, g)` and `f.apply(0, a)`
  ///
  /// @param f a "mapper" which will apply to all (directly) sub nodes of [Term].
  ///          The index indicates how many new bindings are introduced.
  /// @implNote Implements [Term#bindAt] and [Term#replaceAllFrom] if this term is a leaf node.
  ///           Also, {@param f} should preserve [Closure] (with possible change of the implementation).
  /// @apiNote Note that [Term]s provided by `f` might contain [LocalTerm] (see [BindingIntro]),
  ///          therefore your {@param f} should be able to handle them,
  ///          or don't [#descent] on [Term] that contains [Bound] term if your {@param f} cannot handle them.
  ///          Also, [#descent] on a JIT Term may be restricted, only bindings are accessible.
  /// @see BindingIntro
  /// @see Closure
  @Deprecated   // forRemoval = true after we remove all use to `Term#descent(IndexedFunction)`
  static @NotNull TermVisitor ofLegacy(@NotNull IndexedFunction<Term, Term> f) {
    return new TermVisitor() {
      @Override
      public @NotNull Term term(@NotNull Term term) {
        return f.apply(0, term);
      }

      @Override
      public @NotNull Closure closure(@NotNull Closure closure) {
        return closure.descent(t -> f.apply(1, t));
      }
    };
  }
}
