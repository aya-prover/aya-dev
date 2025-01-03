// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat.iter;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.term.DTKind;
import org.aya.syntax.core.term.DepTypeTerm;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PiPusheen implements Pusheenable<Param, @NotNull Term> {
  private final @NotNull ImmutableSeq<Param> unpi;
  private final @NotNull Term result;

  // the index of next param
  private int pusheenIdx = 0;

  public PiPusheen(@NotNull DepTypeTerm.UnpiRaw unpi) {
    this.unpi = unpi.params();
    this.result = unpi.body();
  }

  @Override
  public @NotNull Param peek() {
    return unpi.get(pusheenIdx);
  }

  @Override
  public @NotNull Term body() {
    return DepTypeTerm.makePi(unpi.sliceView(pusheenIdx, unpi.size()).map(Param::type), result);
  }

  @Override
  public boolean hasNext() {
    return pusheenIdx < unpi.size();
  }

  @Override
  public @NotNull Param next() {
    var result = peek();
    pusheenIdx += 1;
    return result;
  }
}
