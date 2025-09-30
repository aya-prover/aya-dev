// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.function.IndexedFunction;
import org.aya.generic.Renamer;
import org.aya.generic.term.DTKind;
import org.aya.generic.term.SortKind;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.annotation.Bound;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.term.marker.Formation;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.aya.syntax.core.term.xtt.CoeTerm;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.ForLSP;
import org.aya.util.Panic;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/// @author re-xyr, kiva, ice1000
public record DepTypeTerm(
  @NotNull DTKind kind, @NotNull Term param,
  @NotNull Closure body
) implements StableWHNF, Formation {
  public @NotNull DepTypeTerm update(@NotNull Term param, @NotNull Closure body) {
    return param == this.param && body == this.body ? this : new DepTypeTerm(kind, param, body);
  }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(f.apply(0, param), body.descent(f));
  }

  /// ```
  ///  i : I |- a : A i
  /// ------------------
  ///   i : I |- B i a
  ///```
  /// It's not the case that {@param a} contains {@param i}, {@param i} only appears in `A` and `B a`
  private Closure.@NotNull Locns abstractBia(@NotNull LocalVar i, Closure a) {
    return body.apply(a.apply(i)).bind(i);
  }

  /// Perform {@code coe} on a dependent type.
  ///
  /// @param i the parameter of {@link CoeTerm#type()}
  public @NotNull Term coe(@NotNull LocalVar i, CoeTerm coe) {
    // We may suppose: pi/sigma = `(a : A i), B i a`,
    // this is i : I |- A i
    var A = param.bind(i);

    return new LamTerm(switch (kind) {
      case Pi -> new Closure.Jit(f -> {
        // We are trying to construct a term for:
        //   coe^{r -> s}_{\i. pi} : pi[r/i] -> pi[s/i]
        // We may intro (f : pi[r/i])
        //   coe^{r -> s}_{\i. pi} f : pi[s/i]

        // In order to construct a term of type `pi[s/i]`, we may use LamTerm
        //   \(x : A s). ??
        return new LamTerm(new Closure.Jit(x -> {
          // We still need to construct the body of lambda of type `B s x`.
          // Recall that we have `f : (a : A r) -> B r a`, we can obtain `A r` by coe `x : A s` backward.
          // coe^{s -> r}_A x : A r
          var fArg = coe.inverse(A).app(x);
          // f fArg : B r fArg
          var fApp = AppTerm.make(f, fArg);

          // We can also obtain `B s x` by coe `f fArg : B r fArg` forward, but we need to choose a path that agree with both side:
          //   We need `a'` that `a'[s/j] = x` and `a'[r/j] = fArg`,
          //   if you look `fArg` closer you may find that if we replace `r` with `j`,
          //   then `fArg = x` when `j` is substituted with `s`
          // a' : coe^{s -> j}_A x
          var aPrime = new Closure.Jit(j -> new CoeTerm(A, coe.s(), j).app(x));
          // coe^{r -> s}_{\j. B j a'} : B r a'[r/j] -> B s x
          var recoe = coe.recoe(j -> abstractBia(i, aPrime).apply(j));
          return recoe.app(fApp);
        }));
      });
      case Sigma -> new Closure.Jit(p -> {
        // We are trying to construct a term for:
        //   coe^{r -> s}_{\i. sigma}: sigma[r/i] -> sigma[s/i]
        // We may intro (p : sigma[r/i])
        //   coe^{r -> s}_{\i. sigma} p : sigma[s/i]
        var fst = ProjTerm.fst(p);
        var snd = ProjTerm.snd(p);

        // In order to construct a term of type `(a : A s) * B s a`, we may use TupTerm
        //   (??, ??)
        // But first, we need a term of type `A s`, one way is coe `p.0 : A r` forward
        //   coe^{r -> s}_A p.0 : A s
        var a = coe.recoe(A).app(fst);
        // and now:
        //   (a, ??)
        // We need to find a term of type `B s a`, similarly, we may coe `p.1 : B r p.0` forward, but we need to choose
        // a path that agree both side:
        //   We need `a'` such that `a'[r/i] = p.0` and `a'[s/i] = a`
        // a' = coe^{r -> j}_A p.0 : A j
        var aPrime = new Closure.Jit(j -> new CoeTerm(A, coe.r(), j).app(fst));
        // coe^{r -> s}_{\j. B j a'} p.1 : B s a
        var b = coe.recoe(j -> abstractBia(i, aPrime).apply(j)).app(snd);
        return new TupTerm(a, b);
      });
    });
  }

  @ForLSP
  public record UnpiNamed(
    @NotNull Seq<Term> params,
    @NotNull Seq<LocalVar> names,
    @NotNull Term body
  ) { }
  @ForLSP public static @NotNull UnpiNamed
  unpi(@NotNull DTKind kind, @NotNull Term term, @NotNull UnaryOperator<Term> pre) {
    return unpi(kind, term, pre, new Renamer());
  }
  @ForLSP public static @NotNull UnpiNamed
  unpi(@NotNull DepTypeTerm term, @NotNull UnaryOperator<Term> pre, @NotNull Renamer nameGen) {
    return unpi(term.kind(), term, pre, nameGen);
  }
  @ForLSP public static @NotNull UnpiNamed
  unpi(@NotNull DTKind kind, @NotNull Term term, @NotNull UnaryOperator<Term> pre, @NotNull Renamer nameGen) {
    var params = MutableList.<Term>create();
    var names = MutableList.<LocalVar>create();
    while (pre.apply(term) instanceof DepTypeTerm(var kk, var param, var body) && kk == kind) {
      params.append(param);
      var var = nameGen.bindName(param);
      names.append(var);
      term = body.apply(var);
    }

    return new UnpiNamed(params, names, term);
  }

  /// db-closeness inherits from the term which this [Unpi] comes from
  public record Unpi(
    @NotNull ImmutableSeq<@Bound Param> params,
    @NotNull @Bound Term body
  ) {
    public Unpi(@NotNull @Bound Term body) {
      this(ImmutableSeq.empty(), body);
    }

    /// @return db-closeness inherits the term which this [Unpi] comes from.
    public @NotNull Term makePi() {
      return DepTypeTerm.makePi(params.view().map(Param::type), body);
    }
  }

  public static @NotNull @Closed Unpi unpiAndBind(
    @NotNull @Closed Term term, @NotNull UnaryOperator<@Closed Term> pre,
    @NotNull MutableList<LocalVar> names
  ) {
    var params = MutableList.<Param>create();
    var i = 0;
    while (pre.apply(term) instanceof DepTypeTerm(var kk, var param, var body) && kk == DTKind.Pi) {
      // Note: PatternTycker depends on the licit of unpi param, be careful to change it!
      params.append(new Param("a" + i++, param.bindTele(names.view()), true));
      var newVar = new LocalVar(Integer.toString(i));
      term = body.apply(newVar);
      names.append(newVar);
    }

    return new Unpi(params.toSeq(), term.bindTele(names.view()));
  }

  public static @NotNull Unpi unpiUnsafe(@NotNull @Bound Term term, int bound) {
    var params = MutableList.<Param>create();
    var i = 0;
    while (i < bound && term instanceof DepTypeTerm(var kk, var param, var body) && kk == DTKind.Pi) {
      // Note: PatternTycker depends on the licit of unpi param, be careful to change it!
      params.append(new Param("a" + i++, param, true));
      term = body.toLocns().body();
    }

    return new Unpi(params.toSeq(), term);
  }

  public static @NotNull SortTerm lubPi(@NotNull SortTerm domain, @NotNull SortTerm codomain) {
    var alift = domain.lift();
    var blift = codomain.lift();
    return switch (domain.kind()) {
      case Type -> switch (codomain.kind()) {
        case Type -> new SortTerm(SortKind.Type, Math.max(alift, blift));
        case ISet, Set -> new SortTerm(SortKind.Set, alift);
      };
      case ISet -> switch (codomain.kind()) {
        case ISet -> SortTerm.Set0;
        case Set, Type -> codomain;
      };
      case Set -> new SortTerm(SortKind.Set, Math.max(alift, blift));
    };
  }

  public static @NotNull SortTerm lubSigma(@NotNull SortTerm x, @NotNull SortTerm y) {
    int lift = Math.max(x.lift(), y.lift());
    return x.kind() == SortKind.Set || y.kind() == SortKind.Set
      ? new SortTerm(SortKind.Set, lift)
      : x.kind() == SortKind.Type || y.kind() == SortKind.Type
        ? new SortTerm(SortKind.Type, lift)
        : x.kind() == SortKind.ISet || y.kind() == SortKind.ISet
          // ice: this is controversial, but I think it's fine.
          // See https://github.com/agda/cubical/pull/910#issuecomment-1233113020
          ? SortTerm.ISet : Panic.unreachable();
  }

  public static @NotNull Term substBody(@NotNull Term pi, @NotNull SeqView<Term> args) {
    for (var arg : args) {
      if (pi instanceof DepTypeTerm realPi) pi = realPi.body.apply(arg);
      else Panic.unreachable();
    }
    return pi;
  }

  @ForLSP
  public static @NotNull Term makePi(@NotNull SeqView<@NotNull @Bound Term> telescope, @NotNull @Bound Term body) {
    return telescope.foldRight(body, (param, cod) -> new DepTypeTerm(DTKind.Pi, param, new Closure.Locns(cod)));
  }
}
