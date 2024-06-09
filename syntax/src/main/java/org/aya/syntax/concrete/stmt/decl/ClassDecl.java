// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete.stmt.decl;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.core.def.ClassDef;
import org.aya.syntax.ref.DefVar;
import org.aya.syntax.ref.MemberVar;
import org.jetbrains.annotations.NotNull;

/**
 * The fields of a class is represented as a telescope,
 * where we introduce the members as definition-level variables.
 */
public final class ClassDecl extends Decl {
  public final @NotNull DefVar<ClassDef, ClassDecl> ref;
  public final @NotNull ImmutableSeq<MemberVar> members;
  public ClassDecl(
    @NotNull String name, @NotNull DeclInfo info,
    @NotNull ImmutableSeq<Expr.Param> telescope
  ) {
    super(info, telescope, null);
    this.ref = DefVar.concrete(this, name);
    this.members = telescope.mapIndexed((i, param) -> new MemberVar(i, param.ref()));
  }
  @Override public @NotNull DefVar<ClassDef, ClassDecl> ref() { return ref; }
}
