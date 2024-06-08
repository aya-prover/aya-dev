// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.ref.DefVar;
import org.jetbrains.annotations.NotNull;

public record ClassDef(
  @Override @NotNull DefVar<ClassDef, ?> ref,
  @NotNull ImmutableSeq<MemberDef> members
) implements TopLevelDef {
  public static class Delegate extends TyckAnyDef<ClassDef> {
    public Delegate(@NotNull DefVar<ClassDef, ?> ref) {
      super(ref);
    }
  }
}
