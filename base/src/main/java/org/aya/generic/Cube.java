// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import kala.collection.immutable.ImmutableSeq;
import org.aya.guest0x0.cubical.Restr;
import org.aya.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * Generalized 'generalized path' syntax.
 *
 * @param params  Dimension parameters.
 * @param clauses Partial element carried by this path.
 * @see org.aya.concrete.Expr.Path
 * @see org.aya.core.term.FormTerm.Path
 */
public record Cube<Term extends Restr.TermLike<Term> & AyaDocile>(
  @NotNull ImmutableSeq<LocalVar> params,
  @NotNull Term type,
  @NotNull ImmutableSeq<Restr.Side<Term>> clauses
) {
  public @NotNull Cube<Term> map(@NotNull ImmutableSeq<LocalVar> params, @NotNull Function<Term, Term> mapper) {
    var type = mapper.apply(this.type);
    var clauses = this.clauses.map(c -> c.rename(mapper));
    if (type == this.type && clauses.sameElements(this.clauses, true)) return this;
    return new Cube<>(params, type, clauses);
  }

  public @NotNull Cube<Term> map(@NotNull Function<Term, Term> mapper) {
    return map(params, mapper);
  }
}
