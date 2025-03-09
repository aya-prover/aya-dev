// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete.stmt.decl;

import org.aya.generic.stmt.TyckUnit;
import org.aya.syntax.concrete.Named;
import org.aya.syntax.concrete.stmt.BindBlock;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.ref.DefVar;
import org.aya.util.binop.OpDecl;
import org.aya.util.position.SourceNode;
import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Concrete telescopic definition, corresponding to {@link TyckDef}.
 *
 * @author re-xyr
 * @see Decl
 */
public sealed abstract class Decl implements SourceNode, Named, Stmt, TyckUnit, OpDecl
  permits ClassDecl, TeleDecl {
  public @NotNull DeclInfo info;
  public boolean isExample;
  public @NotNull PragmaInfo pragmaInfo = new PragmaInfo();

  public final @NotNull BindBlock bindBlock() { return info.bindBlock(); }
  public final @NotNull SourcePos entireSourcePos() { return info.entireSourcePos(); }
  @Override public @NotNull SourcePos nameSourcePos() { return info.sourcePos(); }
  // TODO: use info.entireSourcePos after TyckUnit extends Named instead of SourceNode
  @Override public final @NotNull SourcePos sourcePos() { return info.sourcePos(); }
  @Override public final @NotNull Stmt.Accessibility accessibility() { return info.accessibility(); }
  @Override public final @Nullable OpDecl.OpInfo opInfo() { return info.opInfo(); }
  protected Decl(@NotNull DeclInfo info) { this.info = info; }
  @Contract(pure = true) public abstract @NotNull DefVar<?, ?> ref();
}
