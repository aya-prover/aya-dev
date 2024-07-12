// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.compile.JitClass;
import org.jetbrains.annotations.NotNull;

public sealed interface ClassDefLike extends AnyDef permits JitClass, ClassDef.Delegate {
  @NotNull ImmutableSeq<MemberDefLike> members();
}
