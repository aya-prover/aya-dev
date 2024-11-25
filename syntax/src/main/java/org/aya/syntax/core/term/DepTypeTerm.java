// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.function.IndexedFunction;
import org.aya.generic.Renamer;
import org.aya.generic.term.SortKind;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.term.marker.Formation;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.ForLSP;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/**
 * @author re-xyr, kiva, ice1000
 */
public record DepTypeTerm(@NotNull DTKind kind, @NotNull Term param, @NotNull Closure body) implements StableWHNF, Formation {
  public enum DTKind {
    Pi, Sigma
  }
  public @NotNull DepTypeTerm update(@NotNull Term param, @NotNull Closure body) {
    return param == this.param && body == this.body ? this : new DepTypeTerm(kind, param, body);
  }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(f.apply(0, param), body.descent(f));
  }

  public record Unpi(
    @NotNull Seq<Term> params,
    @NotNull Seq<LocalVar> names,
    @NotNull Term body
  ) { }
  public static @NotNull Unpi unpi(@NotNull DTKind kind, @NotNull Term term, @NotNull UnaryOperator<Term> pre) {
    return unpi(kind, term, pre, new Renamer());
  }
  @ForLSP public static @NotNull Unpi
  unpi(@NotNull DepTypeTerm term, @NotNull UnaryOperator<Term> pre, @NotNull Renamer nameGen) {
    return unpi(term.kind(), term, pre, nameGen);
  }
  @ForLSP public static @NotNull Unpi
  unpi(@NotNull DTKind kind, @NotNull Term term, @NotNull UnaryOperator<Term> pre, @NotNull Renamer nameGen) {
    var params = MutableList.<Term>create();
    var names = MutableList.<LocalVar>create();
    while (pre.apply(term) instanceof DepTypeTerm(var kk, var param, var body) && kk == kind) {
      params.append(param);
      var var = nameGen.bindName(param);
      names.append(var);
      term = body.apply(var);
    }

    return new Unpi(params, names, term);
  }
  public record UnpiRaw(
    @NotNull ImmutableSeq<Param> params,
    @NotNull Term body
  ) { }
  public static @NotNull UnpiRaw unpiDBI(@NotNull Term term, @NotNull UnaryOperator<Term> pre) {
    var params = MutableList.<Param>create();
    var i = 0;
    while (pre.apply(term) instanceof DepTypeTerm(var kk, var param, var body) && kk == DTKind.Pi) {
      params.append(new Param(Integer.toString(i++), param, true));
      term = body.toLocns().body();
    }

    return new UnpiRaw(params.toImmutableSeq(), term);
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

  // public @NotNull LamTerm coe(@NotNull CoeTerm coe, @NotNull LamTerm.Param varI) {
  //   var M = new LamTerm.Param(new LocalVar("f"), true);
  //   var a = new LamTerm.Param(new LocalVar("a"), param.explicit());
  //   var arg = AppTerm.make(coe.inverse(new LamTerm(varI, param.type()).rename()), new Arg<>(a.toTerm(), true));
  //   var cover = CoeTerm.cover(varI, param, body, a.toTerm(), coe.s()).rename();
  //   return new LamTerm(M, new LamTerm(a,
  //     AppTerm.make(coe.recoe(cover),
  //       new Arg<>(AppTerm.make(M.toTerm(), new Arg<>(arg, param.explicit())), true))));
  // }

  public static @NotNull Term substBody(@NotNull Term pi, @NotNull SeqView<Term> args) {
    for (var arg : args) {
      if (pi instanceof DepTypeTerm realPi) pi = realPi.body.apply(arg);
      else Panic.unreachable();
    }
    return pi;
  }

  @ForLSP public static @NotNull Term makePi(@NotNull SeqView<@NotNull Term> telescope, @NotNull Term body) {
    return telescope.foldRight(body, (param, cod) -> new DepTypeTerm(DTKind.Pi, param, new Closure.Locns(cod)));
  }
}
