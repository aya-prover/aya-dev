// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free;

import kala.collection.immutable.primitive.ImmutableIntSeq;
import org.aya.compiler.free.data.LocalVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

public interface FreeCodeBuilder {
  @NotNull FreeJavaResolver resolver();
  @NotNull FreeExprBuilder exprBuilder();

  @NotNull LocalVariable makeVar(@NotNull ClassDesc type, @Nullable FreeJava initializer);

  default LocalVariable makeVar(@NotNull Class<?> type, @Nullable FreeJava initializer) {
    return makeVar(FreeUtil.fromClass(type), initializer);
  }

  void updateVar(@NotNull LocalVariable var, @NotNull FreeJava update);

  void updateArray(@NotNull FreeJava array, int idx, @NotNull FreeJava update);

  void ifNotTrue(@NotNull FreeJava notTrue, @NotNull Consumer<FreeCodeBuilder> thenBlock, @Nullable Consumer<FreeCodeBuilder> elseBlock);
  void ifTrue(@NotNull FreeJava theTrue, @NotNull Consumer<FreeCodeBuilder> thenBlock, @Nullable Consumer<FreeCodeBuilder> elseBlock);
  void ifInstanceOf(@NotNull FreeJava lhs, @NotNull ClassDesc rhs, @NotNull BiConsumer<FreeCodeBuilder, LocalVariable> thenBlock, @Nullable Consumer<FreeCodeBuilder> elseBlock);
  void ifIntEqual(@NotNull FreeJava lhs, int rhs, @NotNull Consumer<FreeCodeBuilder> thenBlock, @Nullable Consumer<FreeCodeBuilder> elseBlock);
  void ifRefEqual(@NotNull FreeJava lhs, @NotNull FreeJava rhs, @NotNull Consumer<FreeCodeBuilder> thenBlock, @Nullable Consumer<FreeCodeBuilder> elseBlock);
  void ifNull(@NotNull FreeJava isNull, @NotNull Consumer<FreeCodeBuilder> thenBlock, @Nullable Consumer<FreeCodeBuilder> elseBlock);

  /**
   * Construct a code block that can jump out
   */
  void breakable(@NotNull Consumer<FreeCodeBuilder> innerBlock);
  void breakOut();

  /**
   * Turns an expression to a statement
   */
  void exec(@NotNull FreeJava expr);

  /**
   * Build a switch statement on int
   */
  void switchCase(
    @NotNull FreeJava elim,
    @NotNull ImmutableIntSeq cases,
    @NotNull ObjIntConsumer<FreeCodeBuilder> branch,
    @NotNull Consumer<FreeCodeBuilder> defaultCase
  );

  void returnWith(@NotNull FreeJava expr);
}
