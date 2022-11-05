// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

public record MatchTerm(
  @NotNull ImmutableSeq<Term> discriminant,
  @NotNull ImmutableSeq<Matching> clauses
) implements Term {
}
