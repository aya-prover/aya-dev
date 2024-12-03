// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.normalize;

import org.aya.generic.term.DTKind;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.xtt.CoeTerm;
import org.aya.syntax.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public interface KanPDF {
  static @NotNull Term coeSigma(@NotNull LocalVar i, @NotNull DepTypeTerm sigma, @NotNull CoeTerm coe) {
    assert sigma.kind() == DTKind.Sigma;

    // We are trying to construct a term for:
    //   coe^{r -> s}_{\i. sigma}: sigma[r/i] -> sigma[s/i]
    // We may intro (p : sigma[r/i])
    //   coe^{r -> s}_{\i. sigma} p : sigma[s/i]
    return new LamTerm(new Closure.Jit(p -> {
      var fst = ProjTerm.make(p, 0);
      var snd = ProjTerm.make(p, 1);

      // We may suppose:
      //  sigma = (a : A i) * B i a
      // i : I |- A i
      var A = sigma.param().bind(i);
      //  i : I |- a : A i
      // ------------------
      //   i : I |- B i a
      // It is impossible that [a] contains [i], [i] only appears in [A] and [B a]
      Function<Closure, Closure> B = a -> sigma.body().apply(a.apply(i)).bind(i);
      // TODO: the code until here is the same as [coePi], maybe unify

      // In order to construct a term of type `(a : A s) * B s a`, we may use TupTerm
      //   (??, ??)
      // But first, we need a term of type `A s`, one way is coe `p.0` forward
      //   coe^{r -> s}_A p.0 : A s
      var a = AppTerm.make(coe.recoe(A), fst);
      // and now:
      //   (a, ??)
      // We need to find a term of type `B s a`, similarly, we may coe `B r p.0` forward, but we need to choose
      // a path that agree both side:
      //   We need `a'` such that `a'[r/i] = p.0` and `a'[s/i] = a`
      // a' = coe^{r -> j}_A p.0 : A j
      var aPrime = new Closure.Jit(j -> AppTerm.make(new CoeTerm(A, coe.r(), j), fst));
      // coe^{r -> s}_{\j. B j a'} p.1 : B s a
      var b = AppTerm.make(coe.recoe(new Closure.Jit(j -> B.apply(aPrime).apply(j))), snd);
      return new TupTerm(a, b);
    }));
  }

  /**
   * Perform {@code coe} on {@code Pi}
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
