// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete.stmt.decl;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.core.def.ClassDef;
import org.aya.syntax.core.def.MemberDef;
import org.aya.syntax.ref.DefVar;
import org.aya.util.position.WithPos;
import org.jetbrains.annotations.NotNull;

public final class ClassMember extends TeleDecl {
  public final @NotNull DefVar<MemberDef, ClassMember> ref;
  public DefVar<ClassDef, ClassDecl> classRef;

  public ClassMember(
    @NotNull String name, @NotNull DeclInfo info,
    @NotNull ImmutableSeq<Expr.Param> telescope, @NotNull WithPos<Expr> result
  ) {
    super(info, telescope, result);
    ref = DefVar.concrete(this, name);
  }

  @Override public @NotNull DefVar<?, ?> ref() { return ref; }
}
