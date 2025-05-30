// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete.stmt.decl;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.stmt.BindBlock;
import org.aya.syntax.core.def.PrimDef;
import org.aya.syntax.ref.DefVar;
import org.aya.util.position.SourcePos;
import org.aya.util.position.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @implSpec the result field of {@link PrimDecl} might be {@link Expr.Error},
 * which means it's unspecified in the concrete syntax.
 * @see PrimDef
 */
public final class PrimDecl extends TeleDecl {
  public final @NotNull DefVar<PrimDef, PrimDecl> ref;

  public PrimDecl(
    @NotNull SourcePos sourcePos, @NotNull SourcePos entireSourcePos,
    @NotNull String name,
    @NotNull ImmutableSeq<Expr.Param> telescope,
    @Nullable WithPos<Expr> result
  ) {
    super(new DeclInfo(Accessibility.Public, sourcePos, entireSourcePos, null, BindBlock.EMPTY), telescope, result);
    ref = DefVar.concrete(this, name);
  }

  @Override public @NotNull DefVar<PrimDef, PrimDecl> ref() { return ref; }
}
