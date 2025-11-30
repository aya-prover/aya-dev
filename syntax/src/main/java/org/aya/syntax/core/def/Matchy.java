// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.TermVisitor;
import org.aya.syntax.core.annotation.Bound;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.QName;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public record Matchy(
  @NotNull Term returnTypeBound,
  @Override @NotNull QName qualifiedName,
  @NotNull ImmutableSeq<Term.Matching> clauses
) implements MatchyLike {
  @Override public @NotNull Term type(@NotNull Seq<Term> captures, @NotNull Seq<Term> args) {
    return returnTypeBound.instTele(captures.view().concat(args));
  }

  public @NotNull Matchy update(@NotNull Term returnTypeBound, @NotNull ImmutableSeq<Term.Matching> clauses) {
    if (this.returnTypeBound == returnTypeBound && this.clauses.sameElements(clauses, true)) return this;
    return new Matchy(returnTypeBound, qualifiedName, clauses);
  }

  public @NotNull Matchy descent(@NotNull UnaryOperator<@Bound Term> f) {
    return update(
      returnTypeBound.descent(TermVisitor.of(f)),
      clauses.map(x -> x.update(f.apply(x.body()))));
  }
}
