// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import org.aya.compiler.LocalVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

public interface CodeBuilder extends ExprBuilder {
  @NotNull LocalVariable makeVar(@NotNull ClassDesc type, @Nullable JavaExpr initializer);

  default LocalVariable makeVar(@NotNull Class<?> type, @Nullable JavaExpr initializer) {
    return makeVar(AstUtil.fromClass(type), initializer);
  }

  void invokeSuperCon(@NotNull ImmutableSeq<ClassDesc> superConParams, @NotNull ImmutableSeq<JavaExpr> superConArgs);
  void updateVar(@NotNull LocalVariable var, @NotNull JavaExpr update);
  void updateArray(@NotNull JavaExpr array, int idx, @NotNull JavaExpr update);
  void ifNotTrue(@NotNull LocalVariable notTrue, @NotNull Consumer<CodeBuilder> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock);
  void ifTrue(@NotNull LocalVariable theTrue, @NotNull Consumer<CodeBuilder> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock);
  void ifInstanceOf(@NotNull JavaExpr lhs, @NotNull ClassDesc rhs, @NotNull BiConsumer<CodeBuilder, LocalVariable> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock);
  void ifIntEqual(@NotNull JavaExpr lhs, int rhs, @NotNull Consumer<CodeBuilder> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock);
  void ifRefEqual(@NotNull JavaExpr lhs, @NotNull JavaExpr rhs, @NotNull Consumer<CodeBuilder> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock);
  void ifNull(@NotNull JavaExpr isNull, @NotNull Consumer<CodeBuilder> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock);

  /// Construct a code block that can jump out
  void breakable(@NotNull Consumer<CodeBuilder> innerBlock);
  void breakOut();

  void whileTrue(@NotNull Consumer<CodeBuilder> innerBlock);
  void continueLoop();

  /// Turns an expression to a statement
  void exec(@NotNull JavaExpr expr);

  /// Build a switch statement on int
  ///
  /// @apiNote the {@param branch}es must return or [#breakOut], this method will NOT generate the `break`
  /// instruction therefore the control flow will pass to the next case.
  void switchCase(
    @NotNull LocalVariable elim,
    @NotNull ImmutableIntSeq cases,
    @NotNull ObjIntConsumer<CodeBuilder> branch,
    @NotNull Consumer<CodeBuilder> defaultCase
  );

  void returnWith(@NotNull JavaExpr expr);
  default void unreachable() {
    returnWith(invoke(Constants.PANIC, ImmutableSeq.empty()));
  }
}
