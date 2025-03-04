// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.ir;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import kala.value.MutableValue;
import org.aya.compiler.morphism.ast.AstExpr;
import org.aya.compiler.morphism.ast.AstVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;

/// For future-proofing, the intermediate representation should have:
/// - Structured control flows (i.e., no jumps) (due to rendering as or generating Java source)
/// - Sufficient abstraction away from the Java source language
public sealed interface IRStmt {
  // TODO: local names
  record DeclVar(@NotNull ClassDesc type, @NotNull AstVariable.Local var) implements IRStmt { }
  record Super(@NotNull ImmutableSeq<ClassDesc> superConParams,
               @NotNull ImmutableSeq<AstExpr> superConArgs) implements IRStmt { }
  record SetVar(@NotNull AstVariable var, @NotNull AstExpr expr) implements IRStmt { }
  record SetArray(@NotNull AstExpr array, int index, @NotNull AstExpr expr) implements IRStmt { }

  sealed interface Condition {
    record IsFalse(@NotNull AstVariable var) implements Condition { }
    record IsTrue(@NotNull AstVariable var) implements Condition { }
    record IsInstanceOf(@NotNull AstExpr lhs, @NotNull ClassDesc rhs,
                        @NotNull MutableValue<AstVariable.Local> asTerm) implements Condition { }
    record IsIntEqual(@NotNull AstExpr lhs, int rhs) implements Condition { }
    record IsRefEqual(@NotNull AstExpr lhs, @NotNull AstExpr rhs) implements Condition { }
    record IsNull(@NotNull AstExpr ref) implements Condition { }
  }

  record IfThenElse(@NotNull Condition cond, @NotNull ImmutableSeq<IRStmt> thenBlock,
                    @Nullable ImmutableSeq<IRStmt> elseBlock) implements IRStmt { }

  // TODO: structured gotos
  record Breakable(@NotNull ImmutableSeq<IRStmt> block) implements IRStmt { }
  enum Break implements IRStmt { INSTANCE }

  enum Unreachable implements IRStmt { INSTANCE }

  record Exec(@NotNull AstExpr expr) implements IRStmt { }
  record Switch(@NotNull AstVariable elim, @NotNull ImmutableIntSeq cases,
                @NotNull ImmutableSeq<ImmutableSeq<IRStmt>> branch,
                @NotNull ImmutableSeq<IRStmt> defaultCase) implements IRStmt { }

  record Return(@NotNull AstExpr expr) implements IRStmt { }
}
