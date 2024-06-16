// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete.stmt.decl;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.core.def.ClassDef;
import org.aya.syntax.ref.DefVar;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.error.PosedUnaryOperator;
import org.jetbrains.annotations.NotNull;

/**
 * The fields of a class is represented as a telescope,
 * where we introduce the members as definition-level variables.
 */
public final class ClassDecl extends Decl {
  public final @NotNull DefVar<ClassDef, ClassDecl> ref;
  public final @NotNull ImmutableSeq<ClassMember> members;
  public final LocalVar self;
  public ClassDecl(
    @NotNull String name, @NotNull DeclInfo info,
    @NotNull ImmutableSeq<ClassMember> members
  ) {
    super(info);
    this.ref = DefVar.concrete(this, name);
    this.members = members;
    members.forEach(member -> member.classRef = ref);
    self = new LocalVar(name + ".self");
  }
  @Override public @NotNull DefVar<ClassDef, ClassDecl> ref() { return ref; }
  @Override public void descentInPlace(@NotNull PosedUnaryOperator<Expr> f, @NotNull PosedUnaryOperator<Pattern> p) {
    members.forEach(x -> x.descentInPlace(f, p));
  }
}
