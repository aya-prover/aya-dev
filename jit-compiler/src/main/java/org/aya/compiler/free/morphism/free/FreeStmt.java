// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism.free;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;

public sealed interface FreeStmt {
  record DeclareVariable(@NotNull ClassDesc type, @NotNull FreeVariable theVar) implements FreeStmt { }
  record Super(@NotNull ImmutableSeq<ClassDesc> superConParams,
               @NotNull ImmutableSeq<FreeExpr> superConArgs) implements FreeStmt { }
  record SetVariable(@NotNull FreeVariable var, @NotNull FreeExpr update) implements FreeStmt { }
  record SetArray(@NotNull FreeExpr array, int index, @NotNull FreeExpr update) implements FreeStmt { }

  sealed interface Condition {
    record IsFalse(@NotNull FreeVariable var) implements Condition { }
    record IsTrue(@NotNull FreeVariable var) implements Condition { }
    record IsInstanceOf(@NotNull FreeExpr lhs, @NotNull ClassDesc rhs,
                        @NotNull FreeVariable asTerm) implements Condition { }
    record IsIntEqual(@NotNull FreeExpr lhs, int rhs) implements Condition { }
    record IsRefEqual(@NotNull FreeExpr lhs, @NotNull FreeExpr rhs) implements Condition { }
    record IsNull(@NotNull FreeExpr ref) implements Condition { }
  }

  record IfThenElse(@NotNull Condition cond, @NotNull ImmutableSeq<FreeStmt> thenBlock,
                    @Nullable ImmutableSeq<FreeStmt> elseBlock) implements FreeStmt { }

  record Breakable(@NotNull ImmutableSeq<FreeStmt> block) implements FreeStmt { }
  enum Break implements FreeStmt { INSTANCE }

  record Exec(@NotNull FreeExpr expr) implements FreeStmt { }
  record Switch(@NotNull FreeVariable elim, @NotNull ImmutableIntSeq cases,
                @NotNull ImmutableSeq<ImmutableSeq<FreeStmt>> branch,
                @NotNull ImmutableSeq<FreeStmt> defaultCase) implements FreeStmt { }

  record Return(@NotNull FreeExpr expr) implements FreeStmt { }
}
