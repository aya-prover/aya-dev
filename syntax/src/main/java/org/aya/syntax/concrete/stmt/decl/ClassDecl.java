// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete.stmt.decl;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.ref.DefVar;
import org.jetbrains.annotations.NotNull;

public final class ClassDecl extends Decl {
  public final @NotNull DefVar<?, ClassDecl> ref;
  public ClassDecl(
    @NotNull String name, @NotNull DeclInfo info,
    @NotNull ImmutableSeq<Expr.Param> telescope
  ) {
    super(info, telescope, null);
    this.ref = DefVar.concrete(this, name);
  }
  @Override public @NotNull DefVar<? extends TyckDef, ?> ref() { return ref; }
}
