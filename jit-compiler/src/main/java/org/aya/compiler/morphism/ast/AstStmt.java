// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ast;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import kala.value.MutableValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;

public sealed interface AstStmt {
  record DeclareVariable(@NotNull ClassDesc type, @NotNull AstVariable.Local theVar) implements AstStmt { }
  record Super(@NotNull ImmutableSeq<ClassDesc> superConParams,
               @NotNull ImmutableSeq<AstExpr> superConArgs) implements AstStmt { }
  record SetVariable(@NotNull AstVariable var, @NotNull AstExpr update) implements AstStmt { }
  record SetArray(@NotNull AstExpr array, int index, @NotNull AstExpr update) implements AstStmt { }

  sealed interface Condition {
    record IsFalse(@NotNull AstVariable var) implements Condition { }
    record IsTrue(@NotNull AstVariable var) implements Condition { }
    record IsInstanceOf(@NotNull AstExpr lhs, @NotNull ClassDesc rhs,
                        @NotNull MutableValue<AstVariable.Local> asTerm) implements Condition { }
    record IsIntEqual(@NotNull AstExpr lhs, int rhs) implements Condition { }
    record IsRefEqual(@NotNull AstExpr lhs, @NotNull AstExpr rhs) implements Condition { }
    record IsNull(@NotNull AstExpr ref) implements Condition { }
  }

  record IfThenElse(@NotNull Condition cond, @NotNull ImmutableSeq<AstStmt> thenBlock,
                    @Nullable ImmutableSeq<AstStmt> elseBlock) implements AstStmt { }

  record Breakable(@NotNull ImmutableSeq<AstStmt> block) implements AstStmt { }
  enum Break implements AstStmt { INSTANCE }
  record WhileTrue(@NotNull ImmutableSeq<AstStmt> block) implements AstStmt { }
  enum Continue implements AstStmt { INSTANCE }
  enum Unreachable implements AstStmt { INSTANCE }

  record Exec(@NotNull AstExpr expr) implements AstStmt { }
  record Switch(@NotNull AstVariable elim, @NotNull ImmutableIntSeq cases,
                @NotNull ImmutableSeq<ImmutableSeq<AstStmt>> branch,
                @NotNull ImmutableSeq<AstStmt> defaultCase) implements AstStmt { }

  record Return(@NotNull AstExpr expr) implements AstStmt { }
}
