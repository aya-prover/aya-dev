// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.MatchCall;
import org.jetbrains.annotations.NotNull;

public record Matchy(
  @NotNull Term returnTypeBound,
  @NotNull ImmutableSeq<Term.Matching> clauses
) implements MatchyLike {
  @Override public @NotNull Term type(@NotNull MatchCall data) {
    return returnTypeBound.instTele(data.captures().view().concat(data.args()));
  }
}
