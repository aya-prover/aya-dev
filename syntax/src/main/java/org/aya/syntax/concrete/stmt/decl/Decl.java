// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete.stmt.decl;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.stmt.TyckUnit;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.concrete.stmt.BindBlock;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.ref.DefVar;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.PosedUnaryOperator;
import org.aya.util.error.SourceNode;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Concrete telescopic definition, corresponding to {@link TyckDef}.
 *
 * @author re-xyr
 * @see Decl
 */
public sealed abstract class Decl implements SourceNode, Stmt, TyckUnit, OpDecl
  permits DataCon, DataDecl, FnDecl, PrimDecl {
  public @Nullable WithPos<Expr> result;
  // will change after resolve
  public @NotNull ImmutableSeq<Expr.Param> telescope;
  public @NotNull DeclInfo info;
  public boolean isExample;

  public final @NotNull BindBlock bindBlock() { return info.bindBlock(); }
  public final @NotNull SourcePos entireSourcePos() { return info.entireSourcePos(); }
  @Override public final @NotNull SourcePos sourcePos() { return info.sourcePos(); }
  @Override public final @NotNull Stmt.Accessibility accessibility() { return info.accessibility(); }
  @Override public final @Nullable OpDecl.OpInfo opInfo() { return info.opInfo(); }

  protected Decl(
    @NotNull DeclInfo info, @NotNull ImmutableSeq<Expr.Param> telescope,
    @Nullable WithPos<Expr> result
  ) {
    this.info = info;
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

  @Contract(pure = true) public abstract @NotNull DefVar<? extends TyckDef, ?> ref();
  public SeqView<LocalVar> teleVars() { return telescope.view().map(Expr.Param::ref); }
}
