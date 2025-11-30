// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.generic.AyaDocile;
import org.aya.generic.TermVisitor;
import org.aya.prettier.BasePrettier;
import org.aya.prettier.CorePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.annotation.Bound;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.annotation.NoInherit;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.term.call.Callable;
import org.aya.syntax.core.term.marker.*;
import org.aya.syntax.core.term.xtt.CoeTerm;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.PrettierOptions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/// The core syntax of Aya. To understand how locally nameless works, see [#bindAllFrom] and [#replaceAllFrom],
/// together with their overrides in [LocalTerm] and [FreeTermLike].
public sealed interface Term extends Serializable, AyaDocile
  permits ClassCastTerm, LetTerm, LocalTerm, Callable, BetaRedex, BindingIntro, Formation, StableWHNF, TyckInternal, CoeTerm {

  @Override default @NotNull Doc toDoc(@NotNull PrettierOptions options) {
    return new CorePrettier(options).term(BasePrettier.Outer.Free, this);
  }

  default @NotNull @Bound Term bindAt(@NotNull LocalVar var, int depth) {
    return bindAllFrom(ImmutableSeq.of(var), depth);
  }

  /// Replacing all [FreeTermLike] of leaf nodes with [LocalTerm] since `fromDepth`.
  ///
  /// the i-th [FreeTermLike#name] in `vars` will be replaced by a [LocalTerm] with index `fromDepth + i`.
  ///
  /// @see #replaceAllFrom
  default @NotNull @Bound Term bindAllFrom(@NotNull ImmutableSeq<LocalVar> vars, int fromDepth) {
    if (vars.isEmpty()) return this;
    return descent((i, t) -> t.bindAllFrom(vars, fromDepth + i));
  }

  /// Corresponds to _abstract_ operator in \[MM 2004\].
  /// However, `abstract` is a keyword in Java, so we can't
  /// use it as a method name.
  /// ```haskell
  /// abstract :: Name → Expr → Scope
  /// ```
  ///
  /// @apiNote bind preserve the term former unless it's a [FreeTerm].
  /// @see Closure#apply(Term)
  /// @see Closure#mkConst
  default @NotNull Closure.Locns bind(@NotNull LocalVar var) {
    return new Closure.Locns(bindAt(var, 0));
  }

  /// Used nontrivially for pattern match expressions, where the clauses are lifted to a global definition,
  /// so after binding the pattern-introduced variables, we need to bind all the free vars,
  /// which will be indexed from the bindCount, rather than 0.
  default @NotNull @Bound Term bindTele(int depth, @NotNull SeqView<LocalVar> teleVars) {
    if (teleVars.isEmpty()) return this;
    return bindAllFrom(teleVars.reversed().toSeq(), depth);
  }

  default @NotNull @Bound Term bindTele(@NotNull SeqView<LocalVar> teleVars) {
    return bindTele(0, teleVars);
  }

  /// Replacing indexes from `from` to `from + list.size()` (exclusive) with `list`,
  /// a [LocalTerm] with index `from + i` will be replaced by `list[i]` if possible.
  ///
  /// @param list a list of term, [Closed] is required
  /// @see #bindAllFrom
  @ApiStatus.Internal
  default @NoInherit @NotNull Term replaceAllFrom(int from, @NotNull ImmutableSeq<@Closed Term> list) {
    if (list.isEmpty()) return this;
    return descent((i, t) -> t.replaceAllFrom(from + i, list));
  }

  /// @see #replaceAllFrom(int, ImmutableSeq)
  /// @see #instTele(SeqView)
  default @NoInherit @NotNull Term instTeleFrom(int from, @NotNull SeqView<@Closed Term> tele) {
    return replaceAllFrom(from, tele.reversed().toSeq());
  }

  /// Corresponds to _instantiate_ operator in \[MM 2004\].
  /// Could be called `apply` similar to Mini-TT, but `apply` is used a lot as method name in Java.
  @ApiStatus.Internal
  default @NoInherit @NotNull Term instantiate(@Closed Term arg) {
    return instTeleFrom(0, SeqView.of(arg));
  }

  /// Instantiate in telescope-order. For example:
  ///
  /// Consider a signature `(?2 : Nat) (?1 : Bool) (?0 : True) -> P ?2 ?0 ?1`,
  /// we can instantiate the result `P ?2 ?0 ?1` by some argument `[ 114514 , false , tt ]`,
  /// now it becomes `P 114514 tt false`.
  /// Without this method, we need to reverse the list.
  default @NoInherit @NotNull Term instTele(@NotNull SeqView<@Closed Term> tele) {
    return instTeleFrom(0, tele);
  }

  default @NoInherit @NotNull Term instTeleVar(@NotNull SeqView<LocalVar> teleVars) {
    return instTele(teleVars.map(FreeTerm::new));
  }

  /// For example, a {@link LamTerm}:
  /// ```
  ///     Γ, a : A ⊢ b : B
  /// --------------------------
  /// Γ ⊢ fn (a : A) => (b : B)
  ///```
  /// `f` will apply to `b`, but the context of `b`: `Γ, a : A` has a new binding,
  /// therefore the implementation should be `f.apply(1, b)`.
  /// In the other hand, a [AppTerm]:
  ///```
  /// Γ ⊢ g : A → B   Γ ⊢ a : A
  /// --------------------------
  ///        Γ ⊢ g a : B
  ///```
  ///`f` will apply to both `g` and `a`, but the context of them have no extra binding,
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
  ///
  /// @see BindingIntro
  /// @see Closure
  @Deprecated
  default @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return descent(t -> f.apply(0, t), c -> c.descent(f));
  }

  @NotNull Term descent(@NotNull TermVisitor visitor);

  default @NotNull Term descent(@NotNull UnaryOperator<Term> onTerm, @NotNull UnaryOperator<Closure> onClosure) {
    return descent(new TermVisitor() {
      @Override
      public @NotNull Term term(@NotNull Term term) {
        return onTerm.apply(term);
      }

      @Override
      public @NotNull Closure closure(@NotNull Closure closure) {
        return onClosure.apply(closure);
      }
    });
  }

  @ApiStatus.NonExtendable
  default @NotNull Term descent(@NotNull UnaryOperator<Term> f) {
    return this.descent((_, t) -> f.apply(t));
  }

  /// Lift the sort level of this term
  ///
  /// @param level level, should be non-negative
  @ApiStatus.NonExtendable
  default @NotNull Term elevate(int level) {
    assert level >= 0 : "level >= 0";
    if (level == 0) return this;
    return doElevate(level);
  }

  default @NotNull Term doElevate(int level) {
    // Assumption : level > 0
    return descent(t -> t.doElevate(level));
  }

  record Matching(@NotNull ImmutableSeq<@Bound Pat> patterns, int bindCount, @NotNull @Bound Term body) {
    public @NotNull Matching update(@NotNull Term body) {
      return body == body() ? this : new Matching(patterns, bindCount, body);
    }

    public @NotNull Matching descent(@NotNull IndexedFunction<Term, Term> f) {
      return update(f.apply(bindCount, body));
    }

    public void forEach(@NotNull Consumer<Term> f, @NotNull Consumer<Pat> g) {
      patterns.forEach(g);
      f.accept(body);
    }
  }
}
