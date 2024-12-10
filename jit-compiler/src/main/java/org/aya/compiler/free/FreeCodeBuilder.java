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

  @NotNull LocalVariable makeVar(@NotNull ClassDesc type, @Nullable FreeJava initializer);

  void ifNotTrue(@NotNull FreeJava notTrue, @NotNull Consumer<FreeCodeBuilder> thenBlock, @Nullable Consumer<FreeCodeBuilder> elseBlock);
  void ifTrue(@NotNull FreeJava theTrue, @NotNull Consumer<FreeCodeBuilder> thenBlock, @Nullable Consumer<FreeCodeBuilder> elseBlock);
  void ifInstanceOf(@NotNull FreeJava lhs, @NotNull ClassDesc rhs, @NotNull BiConsumer<FreeCodeBuilder, LocalVariable> thenBlock, @Nullable Consumer<FreeCodeBuilder> elseBlock);
  void ifNull(@NotNull FreeJava isNull, @NotNull Consumer<FreeCodeBuilder> thenBlock, @Nullable Consumer<FreeCodeBuilder> elseBlock);

  /**
   * Construct a code block that can jump out
   */
  void breakable(@NotNull Consumer<FreeCodeBuilder> innerBlock);
  void breakOut();

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
