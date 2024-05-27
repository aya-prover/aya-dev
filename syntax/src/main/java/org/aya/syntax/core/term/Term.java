// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.generic.AyaDocile;
import org.aya.prettier.BasePrettier;
import org.aya.prettier.CorePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.term.call.Callable;
import org.aya.syntax.core.term.marker.BetaRedex;
import org.aya.syntax.core.term.marker.Formation;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.aya.syntax.core.term.marker.TyckInternal;
import org.aya.syntax.core.term.xtt.CoeTerm;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public sealed interface Term extends Serializable, AyaDocile
  permits BetaRedex, Formation, LocalTerm, StableWHNF, TyckInternal, Callable, CoeTerm {

  @Override default @NotNull Doc toDoc(@NotNull PrettierOptions options) {
    return new CorePrettier(options).term(BasePrettier.Outer.Free, this);
  }

  default @NotNull Term bindAt(@NotNull LocalVar var, int depth) {
    return descent((i, t) -> t.bindAt(var, depth + i));
  }

  /**
   * Corresponds to <emph>abstract</emph> operator in [MM 2004].
   * However, <code>abstract</code> is a keyword in Java, so we can't
   * use it as a method name.
   * <pre>
   * abstract :: Name → Expr → Scope
   * </pre>
   *
   * @apiNote bind preserve the term former unless it's a {@link FreeTerm}.
   * @see Closure#apply(Term)
   * @see Closure#mkConst
   */
  default @NotNull Closure.Idx bind(@NotNull LocalVar var) {
    return new Closure.Idx(bindAt(var, 0));
  }

  /**
   * You might not want to call this method! Use {@link #bindTele} instead.
   *
   * @see #instantiateAll(SeqView)
   * @see #instantiateTele(SeqView)
   */
  private @NotNull Term bindAll(@NotNull SeqView<LocalVar> vars) {
    if (vars.isEmpty()) return this;
    return vars.foldLeftIndexed(this, (idx, acc, var) -> acc.bindAt(var, idx));
  }

  default @NotNull Term bindTele(@NotNull SeqView<LocalVar> teleVars) {
    return bindAll(teleVars.reversed());
  }

  /**
   * Replacing indexes from {@code from} to {@code from + list.size()} (exclusive) with {@code list}
   */
  @ApiStatus.Internal
  default @NotNull Term replaceAllFrom(int from, @NotNull ImmutableSeq<Term> list) {
    if (list.isEmpty()) return this;
    return descent((i, t) -> t.replaceAllFrom(from + i, list));
  }

  /**
   * @see #replaceAllFrom(int, ImmutableSeq)
   * @see #instantiateTele(SeqView)
   */
  default @NotNull Term replaceTeleFrom(int from, @NotNull SeqView<Term> tele) {
    return replaceAllFrom(from, tele.reversed().toImmutableSeq());
  }

  /**
   * Corresponds to <emph>instantiate</emph> operator in [MM 2004].
   * Could be called <code>apply</code> similar to Mini-TT.
   */
  @ApiStatus.Internal
  default @NotNull Term instantiate(Term arg) {
    return replaceAllFrom(0, ImmutableSeq.of(arg));
  }

  /**
   * Instantiate in telescope-order. For example:<br/>
   * Consider a signature {@code (?2 : Nat) (?1 : Bool) (?0 : True) -> P ?2 ?0 ?1},
   * we can instantiate the result {@code P ?2 ?0 ?1} by some argument {@code [ 114514 , false , tt ] },
   * now it becomes {@code P 114514 tt false}.
   * Without this method, we need to reverse the list.
   */
  default @NotNull Term instantiateTele(@NotNull SeqView<Term> tele) {
    return instantiateAll(tele.reversed());
  }

  default @NotNull Term instantiateTeleVar(@NotNull SeqView<LocalVar> teleVars) {
    return instantiateAllVars(teleVars.reversed());
  }

  /**
   * Instantiate {@code 0..args.size() - 1} with {@param args}.
   * You might not want to call this method! Use {@link #instantiateTele} instead.
   *
   * @see #instantiateTele(SeqView)
   */
  default @NotNull Term instantiateAll(@NotNull SeqView<Term> args) {
    return replaceAllFrom(0, args.toImmutableSeq());
  }

  /**
   * You might not want to call this method! Use {@link #instantiateTeleVar} instead.
   *
   * @see #instantiateTeleVar(SeqView)
   */
  default @NotNull Term instantiateAllVars(@NotNull SeqView<LocalVar> args) {
    return instantiateAll(args.map(FreeTerm::new));
  }

  /**
   * For example, a {@link LamTerm}:
   * <pre>
   *     Γ, a : A ⊢ b : B
   * --------------------------
   * Γ ⊢ fn (a : A) => (b : B)
   * </pre>
   * {@code f} will apply to {@code b}, but the context of {@code b}: `Γ, a : A` has a new binding,
   * therefore the implementation should be {@code f.apply(1, b)}.
   * In the other hand, a {@link AppTerm}:
   * <pre>
   *  Γ ⊢ g : A → B   Γ ⊢ a : A
   *  --------------------------
   *         Γ ⊢ g a : B
   *  </pre>
   * {@code f} will apply to both {@code g} and {@code a}, but the context of them have no extra binding,
   * so the implementation should be {@code f.apply(0, g)} and {@code f.apply(0, a)}
   *
   * @param f a "mapper" which will apply to all sub nodes of {@link Term}.
   *          The index indicates how many new bindings are introduced.
   * @implNote implements {@link Term#bindAt} and {@link Term#replaceAllFrom} if this term is a leaf node.
   * Also, {@param f} should preserve {@link Closure} (with possible change of the implementation).
   * @apiNote Note that {@link Term}s provided by {@param f} might contain {@link LocalTerm},
   * therefore your {@param f} should be able to handle them.
   */
  @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f);

  @ApiStatus.NonExtendable
  default @NotNull Term descent(@NotNull UnaryOperator<Term> f) {
    return this.descent((_, t) -> f.apply(t));
  }

  /**
   * Lift the sort level of this term
   *
   * @param level level, should be non-negative
   */
  @ApiStatus.NonExtendable
  default @NotNull Term elevate(int level) {
    assert level >= 0 : "level >= 0";
    if (level == 0) return this;
    return doElevate(level);
  }

  default @NotNull Term doElevate(int level) {
    // Assumption : level > 0
    return descent((_, t) -> t.doElevate(level));
  }

  record Matching(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<Pat> patterns,
    @NotNull Term body
  ) implements AyaDocile {
    @Override public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
      return Pat.Preclause.weaken(this).toDoc(options);
    }

    public @NotNull Matching update(@NotNull ImmutableSeq<Pat> patterns, @NotNull Term body) {
      return body == body() && patterns.sameElements(patterns(), true) ? this
        : new Matching(sourcePos, patterns, body);
    }

    public @NotNull Matching descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
      return update(patterns.map(g), f.apply(body));
    }

    public void forEach(@NotNull Consumer<Term> f, @NotNull Consumer<Pat> g) {
      patterns.forEach(g);
      f.accept(body);
    }
  }
}
