// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete.stmt.decl;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.core.def.DataDef;
import org.aya.syntax.ref.DefVar;
import org.aya.util.error.PosedUnaryOperator;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Concrete data definition
 *
 * @author kiva
 * @see DataDef
 */
public final class DataDecl extends Decl {
  public final @NotNull DefVar<DataDef, org.aya.syntax.concrete.stmt.decl.DataDecl> ref;
  public final @NotNull ImmutableSeq<DataCon> body;

  public DataDecl(
    @NotNull DeclInfo info,
    @NotNull String name,
    @NotNull ImmutableSeq<Expr.Param> telescope,
    @Nullable WithPos<Expr> result,
    @NotNull ImmutableSeq<DataCon> body
  ) {
    super(info, telescope, result);
    this.body = body;
    this.ref = DefVar.concrete(this, name);
    body.forEach(con -> con.dataRef = ref);
  }

  @Override public void descentInPlace(@NotNull PosedUnaryOperator<Expr> f, @NotNull PosedUnaryOperator<Pattern> p) {
    super.descentInPlace(f, p);
    body.forEach(con -> con.descentInPlace(f, p));
  }

  @Override public @NotNull DefVar<DataDef, org.aya.syntax.concrete.stmt.decl.DataDecl> ref() { return ref; }
}
