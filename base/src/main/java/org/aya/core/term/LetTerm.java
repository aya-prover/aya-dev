// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.visitor.Subst;
import org.jetbrains.annotations.NotNull;

public record LetTerm(
  @NotNull ImmutableSeq<LetBind> letBinds,
  @NotNull Term body
) implements Term {
  public record LetBind(
    @NotNull Term.Param name,
    @NotNull Term body
  ) {}

  // construct a subst from a let sequence
  public @NotNull Subst buildSubst() {
    // build from the opposite order
    return letBinds().view().foldRight(new Subst(), (l, r) ->
      r.add(l.name().ref(), l.body()));
  }
}
