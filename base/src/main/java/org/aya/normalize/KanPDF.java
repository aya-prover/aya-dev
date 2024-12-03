// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.normalize;

import org.aya.generic.term.DTKind;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.term.AppTerm;
import org.aya.syntax.core.term.DepTypeTerm;
import org.aya.syntax.core.term.LamTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.xtt.CoeTerm;
import org.aya.syntax.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public interface KanPDF {
  /**
   * Perform {@code coe} on {@code Pi}
   * <pre>
   *   pi = (a : A i) -> B a i
   *   f : pi[r/i]
   *   coe^{r -> s}_{\i. pi} f : pi[s/i]
   *   = \(x : A s). ??
   *
   *
   * </pre>
   *
   * @param i   the parameter of {@link CoeTerm#type()}
   * @param pi  the codomain of {@link CoeTerm#type()}, instantiated with {@param i}
   * @param coe the {@link CoeTerm}
   */
  static @NotNull Term coePi(@NotNull LocalVar i, @NotNull DepTypeTerm pi, @NotNull CoeTerm coe) {
    assert pi.kind() == DTKind.Pi;

    // We are trying to construct a term for:
    //   coe^{r -> s}_{\i. pi} : pi[r/i] -> pi[s/i]
    // We may intro (f : pi[r/i])
    //   coe^{r -> s}_{\i. pi} f : pi[s/i]
    return new LamTerm(new Closure.Jit(f -> {
      // We may suppose:
      //   pi = (a : A i) -> B i a
      // i : I |- A i
      var A = pi.param().bind(i);
      //  i : I |- a : A i
      // ------------------
      //   i : I |- B i a
      // It is impossible that [a] contains [i], [i] only appears in [A] and [B a]
      Function<Closure, Closure> B = a -> pi.body().apply(a.apply(i)).bind(i);

      // In order to construct a term of type `pi[s/i]`, we may use LamTerm
      //   \(x : A s). ??
      return new LamTerm(new Closure.Jit(x -> {
        // We still need to construct the body of lambda of type `B s x`.
        // Recall that we have `f : (a : A r) -> B r a`, we can obtain `A r` by coe `A s` backward.
        // coe^{s -> r}_A x : A r
        var fArg = AppTerm.make(coe.inverse(A), x);
        // f fArg : B r fArg
        var fApp = AppTerm.make(f, fArg);

        // We can also obtain `B s x` by coe `B r fArg` forward, but we need to choose a path that agree with both side:
        //   We need `a'` that `a'[s/j] = x` and `a'[r/j] = fArg`,
        //   if you look `fArg` closer you may find that if we replace `r` with `j`,
        //   then `fArg = x` when `j` is substituted with `s`
        // a' : coe^{s -> j}_A x
        var aPrime = new Closure.Jit(j -> AppTerm.make(new CoeTerm(A, coe.s(), j), x));
        // coe^{r -> s}_{\j. B j a'} : B r a'[r/j] -> B s x
        var recoe = coe.recoe(new Closure.Jit(j -> B.apply(aPrime).apply(j)));
        return AppTerm.make(recoe, fApp);
      }));
    }));
  }
}
