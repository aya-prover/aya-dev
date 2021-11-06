// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.resolve;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import org.jetbrains.annotations.NotNull;

public record ShallowResolveInfo(
  @NotNull DynamicSeq<ImmutableSeq<String>> imports
) {
}
