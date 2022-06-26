// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.Expr;
import org.aya.core.def.Def;
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
 * @author zaoqi
 * @see DefVar
 */
public sealed interface Decl extends OpDecl, SourceNode, TyckUnit, Stmt permits CommonDecl {
  enum Personality {
    NORMAL,
    EXAMPLE,
    COUNTEREXAMPLE,
  }

  @Override @NotNull Accessibility accessibility();
  @Contract(pure = true) @NotNull DefVar<?, ?> ref();
  @NotNull BindBlock bindBlock();
  @Override @Nullable OpInfo opInfo();
  @NotNull SourcePos entireSourcePos();

  @Override default boolean needTyck(@NotNull ImmutableSeq<String> currentMod) {
    return ref().isInModule(currentMod) && ref().core == null;
  }

  sealed interface Telescopic permits TeleDecl, TeleDecl.DataCtor, TeleDecl.StructField {
    @NotNull ImmutableSeq<Expr.Param> telescope();
    void setTelescope(@NotNull ImmutableSeq<Expr.Param> telescope);
    @Nullable Def.Signature signature();
    void setSignature(@Nullable Def.Signature signature);
  }

  sealed interface TopLevel permits ClassDecl, TeleDecl {
    @NotNull Personality personality();
    @Nullable Context getCtx();
    void setCtx(@NotNull Context ctx);
  }

  sealed interface Resulted permits ClassDecl, TeleDecl, TeleDecl.StructField {
    @NotNull Expr result();
    void setResult(@NotNull Expr result);
  }
}
