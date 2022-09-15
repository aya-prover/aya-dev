// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
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

  /**
   * @author ice1000
   */
  record Univ(int lift) implements FormTerm, StableWHNF {
    public static final @NotNull FormTerm.Univ ZERO = new Univ(0);
  }

  /** partial type */
  record PartTy(@NotNull Term type, @NotNull Restr<Term> restr) implements FormTerm {}

  /** generalized path type */
  record Path(@NotNull Cube cube) implements FormTerm, StableWHNF {}

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
    public @NotNull FormTerm.Cube map(@NotNull ImmutableSeq<LocalVar> params, @NotNull Function<Term, Term> mapper) {
      var ty = mapper.apply(type);
      var par = partial.map(mapper);
      if (ty == type && par == partial) return this;
      return new Cube(params, ty, par);
    }

    public @NotNull FormTerm.Cube map(@NotNull Function<Term, Term> mapper) {
      return map(params, mapper);
    }
  }
}
