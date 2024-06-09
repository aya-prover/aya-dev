// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.concrete.stmt.decl.ClassDecl;
import org.aya.syntax.ref.DefVar;
import org.jetbrains.annotations.NotNull;

public record ClassDef(
  @Override @NotNull DefVar<ClassDef, ClassDecl> ref,
  @NotNull ImmutableSeq<MemberDef> members
) implements TopLevelDef {
  public ClassDef { ref.initialize(this); }
  public static final class Delegate extends TyckAnyDef<ClassDef> implements ClassDefLike {
    public Delegate(@NotNull DefVar<ClassDef, ?> ref) { super(ref); }
  }
}
