// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.compile.JitData;
import org.jetbrains.annotations.NotNull;

public sealed interface DataDefLike extends AnyDef permits JitData, DataDef.Delegate {
  @NotNull ImmutableSeq<ConDefLike> body();
}
