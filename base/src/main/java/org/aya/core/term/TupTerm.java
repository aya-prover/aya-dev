// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.util.Arg;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author re-xyr
 */
public record TupTerm(@NotNull ImmutableSeq<Arg<Term>> items) implements StableWHNF {
  @Contract("_ -> new") public static @NotNull TupTerm
  explicits(@NotNull ImmutableSeq<Term> explicits) {
    return new TupTerm(explicits.map(i -> new Arg<>(i, true)));
  }
}
