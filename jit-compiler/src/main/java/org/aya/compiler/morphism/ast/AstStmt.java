// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ast;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import kala.value.MutableValue;
import org.aya.compiler.FieldRef;
import org.aya.prettier.BasePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.Locale;

public sealed interface AstStmt extends Docile {
  @Override default @NotNull Doc toDoc() {
    return Doc.plain("<unimplemented pretty print>");
  }

  record DeclareVariable(@NotNull ClassDesc type, @NotNull AstVariable.Local theVar) implements AstStmt {
    @Override public @NotNull Doc toDoc() {
      return Doc.sep(Doc.styled(BasePrettier.KEYWORD, "let"),
        Doc.cat(theVar.toDoc(), Doc.plain(":")),
        Doc.plain(type.descriptorString()));
    }
  }

  record Super(@NotNull ImmutableSeq<ClassDesc> superConParams,
               @NotNull ImmutableSeq<AstVariable> superConArgs) implements AstStmt { }

  record SetVariable(@NotNull AstVariable var, @NotNull AstExpr update) implements AstStmt {
    @Override public @NotNull Doc toDoc() {
      return Doc.sep(var.toDoc(), Doc.symbol(":="), update.toDoc());
    }
  }

  record SetStaticField(@NotNull FieldRef var, @NotNull AstVariable update) implements AstStmt { }

  record SetArray(@NotNull AstVariable array, int index, @NotNull AstVariable update) implements AstStmt {
    @Override public @NotNull Doc toDoc() {
      return Doc.sep(
        Doc.cat(array.toDoc(), Doc.plain("[" + index + "]")),
        Doc.symbol(":="),
        update.toDoc()
      );
    }
  }

  sealed interface Condition {
    record IsFalse(@NotNull AstVariable var) implements Condition { }
    record IsTrue(@NotNull AstVariable var) implements Condition { }
    record IsInstanceOf(@NotNull AstVariable lhs, @NotNull ClassDesc rhs,
                        @NotNull MutableValue<AstVariable.Local> asTerm) implements Condition { }
    record IsIntEqual(@NotNull AstVariable lhs, int rhs) implements Condition { }
    record IsRefEqual(@NotNull AstVariable lhs, @NotNull AstVariable rhs) implements Condition { }
    record IsNull(@NotNull AstVariable ref) implements Condition { }
  }

  record IfThenElse(@NotNull Condition cond, @NotNull ImmutableSeq<AstStmt> thenBlock,
                    @Nullable ImmutableSeq<AstStmt> elseBlock) implements AstStmt { }

  record Breakable(@NotNull ImmutableSeq<AstStmt> block) implements AstStmt { }
  record WhileTrue(@NotNull ImmutableSeq<AstStmt> block) implements AstStmt { }
  enum SingletonStmt implements AstStmt {
    Break,
    Continue,
    Unreachable;

    @Override public @NotNull Doc toDoc() {
      return Doc.styled(BasePrettier.KEYWORD, name().toLowerCase(Locale.ROOT));
    }
  }

  record Exec(@NotNull AstExpr expr) implements AstStmt {
    @Override public @NotNull Doc toDoc() {
      return expr.toDoc();
    }
  }

  record Switch(@NotNull AstVariable elim, @NotNull ImmutableIntSeq cases,
                @NotNull ImmutableSeq<ImmutableSeq<AstStmt>> branch,
                @NotNull ImmutableSeq<AstStmt> defaultCase) implements AstStmt { }

  record Return(@NotNull AstVariable expr) implements AstStmt {
    @Override public @NotNull Doc toDoc() {
      return Doc.sep(Doc.styled(BasePrettier.KEYWORD, "return"), expr.toDoc());
    }
  }
}
