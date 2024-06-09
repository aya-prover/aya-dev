// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete.stmt.decl;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.error.PosedUnaryOperator;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed abstract class TeleDecl extends Decl permits ClassMember, DataCon, DataDecl, FnDecl, PrimDecl {
  public @Nullable WithPos<Expr> result;
  // will change after resolve
  public @NotNull ImmutableSeq<Expr.Param> telescope;

  protected TeleDecl(@NotNull DeclInfo info, @NotNull ImmutableSeq<Expr.Param> telescope, @Nullable WithPos<Expr> result) {
    super(info);
    this.result = result;
    this.telescope = telescope;
  }

  public void modifyResult(@NotNull PosedUnaryOperator<Expr> f) {
    if (result != null) result = result.descent(f);
  }

  @Override public void descentInPlace(@NotNull PosedUnaryOperator<Expr> f, @NotNull PosedUnaryOperator<Pattern> p) {
    telescope = telescope.map(param -> param.descent(f));
    modifyResult(f);
  }

  public SeqView<LocalVar> teleVars() { return telescope.view().map(Expr.Param::ref); }
}
