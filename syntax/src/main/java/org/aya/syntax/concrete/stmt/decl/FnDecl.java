// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete.stmt.decl;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.Modifier;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.core.def.FnDef;
import org.aya.syntax.ref.DefVar;
import org.aya.util.position.PosedUnaryOperator;
import org.aya.util.position.WithPos;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

/**
 * Concrete function definition
 *
 * @author re-xyr
 * @see FnDef
 */
public final class FnDecl extends TeleDecl {
  public final @NotNull EnumSet<Modifier> modifiers;
  public final @NotNull DefVar<FnDef, FnDecl> ref;
  public @NotNull FnBody body;

  public FnDecl(
    @NotNull DeclInfo info,
    @NotNull EnumSet<Modifier> modifiers,
    @NotNull String name,
    @NotNull ImmutableSeq<Expr.Param> telescope,
    @NotNull WithPos<Expr> result,
    @NotNull FnBody body
  ) {
    super(info, telescope, result);

    this.modifiers = modifiers;
    this.ref = DefVar.concrete(this, name);
    this.body = body;
  }

  @Override public @NotNull DefVar<FnDef, FnDecl> ref() { return ref; }

  @Override
  public void descentInPlace(@NotNull PosedUnaryOperator<Expr> f, @NotNull PosedUnaryOperator<Pattern> p) {
    super.descentInPlace(f, p);
    body = body.map(f, cls -> cls.descent(f, p));
  }
}
