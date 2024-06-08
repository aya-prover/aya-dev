// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.tyck;

import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

/**
 * Tyck SCCs. 
 */
public interface SccTycker<T, E extends Exception> {
  /** @return failed items */
  @NotNull ImmutableSeq<T> tyckSCC(@NotNull ImmutableSeq<T> scc) throws E;
}
