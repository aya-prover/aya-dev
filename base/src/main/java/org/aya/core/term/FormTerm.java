// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.core.visitor.Subst;
import org.aya.generic.Arg;
import org.aya.generic.SortKind;
import org.aya.guest0x0.cubical.Partial;
import org.aya.guest0x0.cubical.Restr;
import org.aya.ref.LocalVar;
import org.aya.util.error.SourcePos;
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

  /**
   * @author ice1000
   */
  record Sort(@NotNull SortKind kind, int lift) implements FormTerm, StableWHNF {
    public Sort(@NotNull SortKind kind, int lift) {
      this.kind = kind;
      if (!kind.hasLevel() && lift != 0) throw new IllegalArgumentException("invalid lift");
      this.lift = lift;
    }

    public static Sort Type0 = new Sort(SortKind.Type, 0);
    public static Sort Set0 = new Sort(SortKind.Set, 0);
    public static Sort Set1 = new Sort(SortKind.Set, 1);
    public static Sort ISet = new Sort(SortKind.ISet, 0);
    public static Sort Prop = new Sort(SortKind.Prop, 0);

    public @NotNull FormTerm.Sort succ() {
      return switch (kind) {
        case Type -> new Sort(kind, lift + 1);
        case Set -> new Sort(kind, lift + 1);
        case Prop -> Type0;
        case ISet -> Set1;
      };
    }

    public @NotNull FormTerm.Sort elevate(int lift) {
      if (kind.hasLevel()) return new Sort(kind, this.lift + lift);
      else return this;
    }
  }

  /** partial type */
  record PartTy(@NotNull Term type, @NotNull Restr<Term> restr) implements FormTerm {}

  /** generalized path type */
  record Path(@NotNull Cube cube) implements FormTerm, StableWHNF {
  }

  /**
   * 'Generalized path' syntax.
   *
   * @param params  Dimension parameters, never empty.
   * @param partial Partial element carried by this path.
   * @see org.aya.concrete.Expr.Path
   * @see Path
   */
  record Cube(
    @NotNull ImmutableSeq<LocalVar> params,
    @NotNull Term type,
    @NotNull Partial<Term> partial
  ) {
    public @NotNull Term eta(@NotNull Term term) {
      return new IntroTerm.PathLam(params(), applyDimsTo(term)).rename();
    }

    public @NotNull Pi computePi() {
      var iTele = params().view().map(x -> new Param(x, PrimTerm.Interval.INSTANCE, true));
      return (Pi) Pi.make(iTele, type());
    }

    public @NotNull Term applyDimsTo(@NotNull Term pLam) {
      var args = params.view().map(RefTerm::new);
      loop:
      while (true) {
        if (args.isEmpty()) return pLam;
        switch (pLam) {
          case default -> {
            break loop;
          }
          case IntroTerm.PathLam lam -> {
            assert lam.params().sizeLessThanOrEquals(args);
            pLam = lam.body().subst(new Subst(lam.params(), args.take(lam.params().size())));
            args = args.drop(lam.params().size());
          }
          case IntroTerm.Lambda lam -> {
            // TODO: replace with error report¿
            assert lam.param().explicit();
            pLam = ElimTerm.make(lam, new Arg<>(args.first(), true));
            args = args.drop(1);
          }
        }
      }
      var newArgs = args.map(x -> new Arg<Term>(x, true)).toImmutableSeq();
      return new ElimTerm.PathApp(pLam, newArgs, this);
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

    public @NotNull Term makeApp(@NotNull Term app, @NotNull Arg<Term> arg) {
      return ElimTerm.make(etaLam(app), arg);
    }

    /** "not really eta". Used together with {@link #computePi()} */
    public @NotNull Term etaLam(@NotNull Term term) {
      return params.map(x -> new Param(x, PrimTerm.Interval.INSTANCE, true))
        .foldRight(applyDimsTo(term), IntroTerm.Lambda::new).rename();
    }
  }
}
