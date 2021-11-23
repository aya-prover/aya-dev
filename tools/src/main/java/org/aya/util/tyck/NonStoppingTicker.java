// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.tyck;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableSet;
import org.jetbrains.annotations.NotNull;

/**
 * Non-stopping compiler for SCCs.
 *
 * @author kiva
 */
public interface NonStoppingTicker<T, E extends Exception> {
  @NotNull SCCTycker<T, E> sccTycker();
  @NotNull MutableSet<T> skippedSet();
  @NotNull Iterable<T> collectUsageOf(@NotNull T failed);

  default void tyckSCC(@NotNull ImmutableSeq<T> scc) throws E {
    // we are more likely to check correct programs.
    // I'm not sure whether it's necessary to optimize on our own.
    var sccTycker = sccTycker();
    var skipped = skippedSet();
    if (skipped.isEmpty()) skip(sccTycker.tyckSCC(scc));
    else skip(sccTycker.tyckSCC(scc.filterNot(skipped::contains)));
  }

  private void skip(@NotNull ImmutableSeq<T> failed) {
    var skipped = skippedSet();
    failed.forEach(f -> skip(skipped, f));
  }

  private void skip(@NotNull MutableSet<T> skipped, @NotNull T failed) {
    if (skipped.contains(failed)) return;
    skipped.add(failed);
    collectUsageOf(failed).forEach(f -> skip(skipped, f));
  }
}
