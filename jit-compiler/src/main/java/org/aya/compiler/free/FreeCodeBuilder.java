// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import org.aya.compiler.free.data.FieldRef;
import org.aya.compiler.free.data.LocalVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

public interface FreeCodeBuilder extends FreeExprBuilder {
  @NotNull LocalVariable makeVar(@NotNull ClassDesc type, @Nullable FreeJavaExpr initializer);

  default LocalVariable makeVar(@NotNull Class<?> type, @Nullable FreeJavaExpr initializer) {
    return makeVar(FreeUtil.fromClass(type), initializer);
  }

  void invokeSuperCon(@NotNull ImmutableSeq<ClassDesc> superConParams, @NotNull ImmutableSeq<FreeJavaExpr> superConArgs);

  void updateVar(@NotNull LocalVariable var, @NotNull FreeJavaExpr update);

  void updateArray(@NotNull FreeJavaExpr array, int idx, @NotNull FreeJavaExpr update);

  void updateField(@NotNull FieldRef field, @NotNull FreeJavaExpr update);

  void updateField(@NotNull FieldRef field, @NotNull FreeJavaExpr owner, @NotNull FreeJavaExpr update);

  void ifNotTrue(@NotNull LocalVariable notTrue, @NotNull Consumer<FreeCodeBuilder> thenBlock, @Nullable Consumer<FreeCodeBuilder> elseBlock);

  void ifTrue(@NotNull LocalVariable theTrue, @NotNull Consumer<FreeCodeBuilder> thenBlock, @Nullable Consumer<FreeCodeBuilder> elseBlock);

  void ifInstanceOf(@NotNull FreeJavaExpr lhs, @NotNull ClassDesc rhs, @NotNull BiConsumer<FreeCodeBuilder, LocalVariable> thenBlock, @Nullable Consumer<FreeCodeBuilder> elseBlock);

  void ifIntEqual(@NotNull FreeJavaExpr lhs, int rhs, @NotNull Consumer<FreeCodeBuilder> thenBlock, @Nullable Consumer<FreeCodeBuilder> elseBlock);

  void ifRefEqual(@NotNull FreeJavaExpr lhs, @NotNull FreeJavaExpr rhs, @NotNull Consumer<FreeCodeBuilder> thenBlock, @Nullable Consumer<FreeCodeBuilder> elseBlock);

  void ifNull(@NotNull FreeJavaExpr isNull, @NotNull Consumer<FreeCodeBuilder> thenBlock, @Nullable Consumer<FreeCodeBuilder> elseBlock);

  /**
   * Construct a code block that can jump out
   */
  void breakable(@NotNull Consumer<FreeCodeBuilder> innerBlock);
  void breakOut();

  /**
   * Turns an expression to a statement
   */
  void exec(@NotNull FreeJavaExpr expr);

  /**
   * Build a switch statement on int
   */
  void switchCase(
    @NotNull LocalVariable elim,
    @NotNull ImmutableIntSeq cases,
    @NotNull ObjIntConsumer<FreeCodeBuilder> branch,
    @NotNull Consumer<FreeCodeBuilder> defaultCase
  );

  void returnWith(@NotNull FreeJavaExpr expr);
}
