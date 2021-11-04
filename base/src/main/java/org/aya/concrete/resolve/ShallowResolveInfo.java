// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.resolve;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;

public record ShallowResolveInfo(
  @NotNull Buffer<ImmutableSeq<String>> imports
) {
}
