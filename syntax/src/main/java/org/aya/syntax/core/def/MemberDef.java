// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.DefVar;
import org.jetbrains.annotations.NotNull;

public record MemberDef(
  @NotNull DefVar<ClassDef, ?> classRef,
  @Override @NotNull DefVar<MemberDef, ?> ref,
  @Override ImmutableSeq<Param> telescope,
  @Override @NotNull Term result
) implements TyckDef {
  public static class Delegate extends TyckAnyDef<MemberDef> {
    public Delegate(@NotNull DefVar<MemberDef, ?> ref) { super(ref); }
  }
}
