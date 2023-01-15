// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt.decl;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.BindBlock;
import org.aya.concrete.stmt.Stmt;
import org.aya.ref.DefVar;
import org.aya.resolve.context.Context;
import org.aya.tyck.order.TyckUnit;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourceNode;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Generic concrete definitions, corresponding to {@link org.aya.core.def.GenericDef}.
 * Concrete definitions can be varied in the following ways:
 * <ul>
 *   <li>Whether it has a telescope, see {@link TeleDecl}</li>
 *   <li>Whether it can be defined at top-level, see {@link TopLevel}</li>
 * </ul>
 * We say these are properties of a concrete definition and should be implemented selectively.
 *
 * <p>
 * There are some commonalities between concrete definitions: they all have
 * source positions, names, operator info and statement accessibility. These common
 * parts are extracted into {@link CommonDecl} for all concrete definitions,
 * {@link TeleDecl} for all top-level telescopic concrete definitions and
 * {@link ClassDecl} for all top-level class-able concrete definitions.
 *
 * @author kiva, zaoqi
 * @see CommonDecl
 * @see TeleDecl
 * @see ClassDecl
 */
public sealed interface Decl extends OpDecl, SourceNode, TyckUnit, Stmt permits CommonDecl {
  @Contract(pure = true) @NotNull DefVar<?, ?> ref();
  @Contract(pure = true) @NotNull DeclInfo info();
  default @NotNull BindBlock bindBlock() {
    return info().bindBlock();
  }

  default @NotNull SourcePos entireSourcePos() {
    return info().entireSourcePos();
  }

  @Override default @NotNull SourcePos sourcePos() {
    return info().sourcePos();
  }

  @Override default @NotNull Accessibility accessibility() {
    return info().accessibility();
  }

  @Override default @Nullable OpInfo opInfo() {
    return info().opInfo();
  }

  @Override default boolean needTyck(@NotNull ImmutableSeq<String> currentMod) {
    return ref().isInModule(currentMod) && ref().core == null;
  }

  /**
   * Denotes that the definition can be defined at top-level
   *
   * @author kiva
   */
  sealed interface TopLevel permits ClassDecl, TeleDecl.TopLevel {
    @NotNull DeclInfo.Personality personality();
    @Nullable Context getCtx();
    void setCtx(@NotNull Context ctx);
  }
}
