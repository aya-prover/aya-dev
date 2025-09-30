// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat.iter;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.core.annotation.Bound;
import org.aya.syntax.core.term.DepTypeTerm;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

public final class PiPusheen implements Pusheenable<@Bound Param, @NotNull @Bound Term> {
  private final @NotNull ImmutableSeq<@Bound Param> unpi;
  private final @NotNull @Bound Term result;

  // the index of next param
  private int pusheenIdx = 0;

  public PiPusheen(@NotNull DepTypeTerm.Unpi unpi) {
    this.unpi = unpi.params();
    this.result = unpi.body();
  }

  @Override public @NotNull @Bound Param peek() { return unpi.get(pusheenIdx); }
  @Override public @NotNull @Bound Term body() {
    return unpiBody().makePi();
  }

  /// Mostly [Bound], unless [#pusheenIdx] is 0 and the [org.aya.syntax.core.term.DepTypeTerm.Unpi] we use is closed.
  public @NotNull @Bound DepTypeTerm.Unpi unpiBody() {
    return new DepTypeTerm.Unpi(unpi.sliceView(pusheenIdx, unpi.size()).toSeq(), result);
  }

  @Override public boolean hasNext() { return pusheenIdx < unpi.size(); }
  @Override public @NotNull @Bound Param next() {
    var result = peek();
    pusheenIdx += 1;
    return result;
  }
}
