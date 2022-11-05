// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

public record PLamTerm(
  @NotNull ImmutableSeq<LocalVar> params,
  @NotNull Term body
) implements StableWHNF {}
