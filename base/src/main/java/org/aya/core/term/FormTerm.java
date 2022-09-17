// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.core.visitor.Subst;
import org.aya.generic.Arg;
import org.aya.guest0x0.cubical.Partial;
import org.aya.guest0x0.cubical.Restr;
import org.aya.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * Formation rules.
 *
 * @author ice1000
 */
public sealed interface FormTerm extends Term {
  /**
   * @author re-xyr, kiva, ice1000
   */
  record Pi(@NotNull Term.Param param, @NotNull Term body) implements FormTerm, StableWHNF {

    public @NotNull Term substBody(@NotNull Term term) {
      return body.subst(param.ref(), term);
    }

    public @NotNull Term parameters(@NotNull MutableList<Term.@NotNull Param> params) {
      params.append(param);
      var t = body;
      while (t instanceof Pi pi) {
        params.append(pi.param);
        t = pi.body;
      }
      return t;
    }

    public static @NotNull Term make(@NotNull SeqLike<@NotNull Param> telescope, @NotNull Term body) {
      return telescope.view().foldRight(body, Pi::new);
    }
  }

  static @NotNull Term unpi(@NotNull Term term, @NotNull MutableList<Term.Param> params) {
    while (term instanceof Pi pi) {
      params.append(pi.param);
      term = pi.body;
    }
    return term;
  }

  /**
   * @author re-xyr
   */
  record Sigma(@NotNull ImmutableSeq<@NotNull Param> params) implements FormTerm, StableWHNF {
  }

  enum SortKind {
    Type, Set, Prop, ISet;

    public boolean hasLevel() {
      return this == Type || this == Set;
    }

    public @NotNull SortKind max(@NotNull SortKind other) {
      if (this == Set || other == Set) return Set;
      if (this == Type || other == Type) return Type;
      // Prop or ISet
      return this == other ? this : Type;
    }
  }

  /**
   * @author ice1000
   */
  sealed interface Sort extends FormTerm, StableWHNF {
    int lift();
    @NotNull FormTerm.SortKind kind();
    @NotNull FormTerm.Sort succ();

    static @NotNull Sort create(@NotNull FormTerm.SortKind kind, int lift) {
      return switch (kind) {
        case Type -> new Type(lift);
        case Set -> new Set(lift);
        case Prop -> Prop.INSTANCE;
        case ISet -> ISet.INSTANCE;
      };
    }

    default @NotNull Sort max(@NotNull Sort other) {
      return Sort.create(this.kind().max(other.kind()), Math.max(this.lift(), other.lift()));
    }
  }

  record Type(@Override int lift) implements Sort {
    public static final @NotNull FormTerm.Type ZERO = new Type(0);

    @Override public @NotNull FormTerm.SortKind kind() {
      return SortKind.Type;
    }

    @Override public @NotNull FormTerm.Type succ() {
      return new FormTerm.Type(lift + 1);
    }
  }

  record Set(@Override int lift) implements Sort {
    public static final @NotNull FormTerm.Set ZERO = new Set(0);

    @Override public @NotNull FormTerm.SortKind kind() {
      return SortKind.Set;
    }

    @Override
    public @NotNull FormTerm.Set succ() {
      return new FormTerm.Set(lift + 1);
    }
  }

  final class Prop implements Sort {
    public static final @NotNull Prop INSTANCE = new Prop();

    private Prop() {
    }

    @Override public int lift() {
      return 0;
    }

    @Override public @NotNull FormTerm.SortKind kind() {
      return SortKind.Prop;
    }

    @Override public @NotNull FormTerm.Type succ() {
      return new FormTerm.Type(0);
    }
  }

  final class ISet implements Sort {
    public static final @NotNull ISet INSTANCE = new ISet();

    private ISet() {

    }

    @Override public int lift() {
      return 0;
    }

    @Override public @NotNull FormTerm.SortKind kind() {
      return SortKind.ISet;
    }

    @Override public @NotNull FormTerm.Set succ() {
      return new FormTerm.Set(1);
    }
  }

  /** partial type */
  record PartTy(@NotNull Term type, @NotNull Restr<Term> restr) implements FormTerm {}

  /** generalized path type */
  record Path(@NotNull Cube cube) implements FormTerm, StableWHNF {
  }

  /**
   * Generalized 'generalized path' syntax.
   *
   * @param params  Dimension parameters.
   * @param partial Partial element carried by this path.
   * @see org.aya.concrete.Expr.Path
   * @see Path
   */
  record Cube(
    @NotNull ImmutableSeq<LocalVar> params,
    @NotNull Term type,
    @NotNull Partial<Term> partial
  ) {
    public @NotNull Pi computePi() {
      var iTele = params().view().map(x -> new Param(x, PrimTerm.Interval.INSTANCE, true));
      return (Pi) Pi.make(iTele, type());
    }

    public @NotNull Term applyDimsTo(@NotNull Term innerMost) {
      var args = params.view().map(RefTerm::new);
      if (innerMost instanceof IntroTerm.PathLam lam) {
        assert lam.params().sizeEquals(params());
        return lam.body().subst(new Subst(lam.params(), args));
      }
      var newArgs = args.map(x -> new Arg<Term>(x, true));
      if (innerMost instanceof IntroTerm.Lambda) {
        return newArgs.foldLeft(innerMost, CallTerm::make);
      }
      return new ElimTerm.PathApp(innerMost, newArgs.toImmutableSeq(), this);
    }

    public @NotNull FormTerm.Cube map(@NotNull ImmutableSeq<LocalVar> params, @NotNull Function<Term, Term> mapper) {
      var ty = mapper.apply(type);
      var par = partial.map(mapper);
      if (ty == type && par == partial) return this;
      return new Cube(params, ty, par);
    }

    public @NotNull FormTerm.Cube map(@NotNull Function<Term, Term> mapper) {
      return map(params, mapper);
    }

    public Term makeApp(@NotNull Term app, @NotNull Arg<Term> arg) {
      var xi = params().map(x -> new Term.Param(x, PrimTerm.Interval.INSTANCE, true));
      var elim = new ElimTerm.PathApp(app, xi.map(Term.Param::toArg), this);
      var lam = xi.foldRight((Term) elim, IntroTerm.Lambda::new).rename();
      // ^ the cast is necessary, see https://bugs.openjdk.org/browse/JDK-8292975
      return CallTerm.make(lam, arg);
    }
  }
}
