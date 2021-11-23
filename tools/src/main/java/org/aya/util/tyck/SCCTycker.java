// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.tyck;

import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

/**
 * Tyck SCCs. 
 */
public interface SCCTycker<T, E extends Exception> {
  /** @return failed items */
  @NotNull ImmutableSeq<T> tyckSCC(@NotNull ImmutableSeq<T> scc) throws E;
}
