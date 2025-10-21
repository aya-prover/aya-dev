// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ast;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableSeq;
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

  record DeclareVariable(
    @NotNull ClassDesc type,
    @NotNull AstVariable.Local theVar,
    @Nullable AstExpr initializer
  ) implements AstStmt {
    @Override public @NotNull Doc toDoc() {
      var list = MutableList.of(
        Doc.styled(BasePrettier.KEYWORD, "let"),
        Doc.cat(theVar.toDoc(), Doc.plain(":")),
        Doc.plain(type.displayName())
      );
      if (initializer != null) {
        list.append(Doc.symbol(":="));
        list.append(initializer.toDoc());
      }
      return Doc.sep(list);
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
        Doc.cat(array.toDoc(), Doc.wrap("[", "]", Doc.plain(String.valueOf(index)))),
        Doc.symbol(":="),
        update.toDoc()
      );
    }
  }

  sealed interface Condition extends Docile {
    record IsFalse(@NotNull AstVariable var) implements Condition {
      @Override public @NotNull Doc toDoc() {
        return Doc.sep(Doc.styled(BasePrettier.KEYWORD, "unless"), var.toDoc());
      }
    }
    record IsTrue(@NotNull AstVariable var) implements Condition {
      @Override public @NotNull Doc toDoc() {
        return Doc.sep(Doc.styled(BasePrettier.KEYWORD, "if"), var.toDoc());
      }
    }
    record IsInstanceOf(@NotNull AstVariable lhs, @NotNull ClassDesc rhs,
                        @NotNull MutableValue<AstVariable.Local> asTerm) implements Condition {
      @Override public @NotNull Doc toDoc() {
        return Doc.sep(
          Doc.styled(BasePrettier.KEYWORD, "if let"),
          Doc.cat(asTerm.get().toDoc(), Doc.plain(":")),
          Doc.plain(rhs.displayName()),
          Doc.styled(BasePrettier.KEYWORD, ":="),
          lhs.toDoc()
        );
      }
    }
    record IsIntEqual(@NotNull AstVariable lhs, int rhs) implements Condition {
      @Override public @NotNull Doc toDoc() {
        return Doc.sep(Doc.styled(BasePrettier.KEYWORD, "if int eq"), lhs.toDoc(), Doc.plain(String.valueOf(rhs)));
      }
    }
    record IsRefEqual(@NotNull AstVariable lhs, @NotNull AstVariable rhs) implements Condition {
      @Override public @NotNull Doc toDoc() {
        return Doc.sep(Doc.styled(BasePrettier.KEYWORD, "if ref eq"), lhs.toDoc(), rhs.toDoc());
      }
    }
    record IsNull(@NotNull AstVariable ref) implements Condition {
      @Override public @NotNull Doc toDoc() {
        return Doc.sep(Doc.styled(BasePrettier.KEYWORD, "if null"), ref.toDoc());
      }
    }
  }

  record IfThenElse(@NotNull Condition cond, @NotNull ImmutableSeq<AstStmt> thenBlock,
                    @Nullable ImmutableSeq<AstStmt> elseBlock) implements AstStmt {
    @Override public @NotNull Doc toDoc() {
      var list = MutableList.of(
        cond.toDoc(),
        Doc.nest(2, Doc.vcat(thenBlock.view().map(AstStmt::toDoc))));
      if (elseBlock != null && elseBlock.isNotEmpty()) {
        list.append(Doc.styled(BasePrettier.KEYWORD, "else"));
        list.append(Doc.nest(2, Doc.vcat(elseBlock.view().map(AstStmt::toDoc))));
      }
      return Doc.vcat(list);
    }
  }

  record Breakable(@NotNull ImmutableSeq<AstStmt> block) implements AstStmt {
    @Override public @NotNull Doc toDoc() {
      return Doc.vcat(
        Doc.styled(BasePrettier.KEYWORD, "breakable"),
        Doc.nest(2, Doc.vcat(block.view().map(AstStmt::toDoc)))
      );
    }
  }

  record WhileTrue(@NotNull ImmutableSeq<AstStmt> block) implements AstStmt {
    @Override public @NotNull Doc toDoc() {
      return Doc.vcat(
        Doc.styled(BasePrettier.KEYWORD, "loop"),
        Doc.nest(2, Doc.vcat(block.view().map(AstStmt::toDoc)))
      );
    }
  }

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
                @NotNull ImmutableSeq<AstStmt> defaultCase) implements AstStmt {
    @Override public @NotNull Doc toDoc() {
      var size = cases.size();
      if (defaultCase.isNotEmpty()) size++;
      var caseDocs = Doc.sep(Doc.styled(BasePrettier.KEYWORD, "switch"),
        elim.toDoc(),
        Doc.styled(BasePrettier.KEYWORD, "amongst"),
        Doc.plain(String.valueOf(size)));
      var branchesDoc = MutableSeq.<Doc>create(size);
      for (int i = 0; i < cases.size(); i++) {
        var stmts = branch.get(i);
        if (stmts.sizeEquals(1)) {
          branchesDoc.set(i, Doc.sep(
            Doc.styled(BasePrettier.KEYWORD, "case"),
            Doc.plain(String.valueOf(cases.get(i))),
            Doc.symbol("->"),
            stmts.getFirst().toDoc()
          ));
        } else {
          branchesDoc.set(i, Doc.vcat(
            Doc.sep(
              Doc.styled(BasePrettier.KEYWORD, "case"),
              Doc.plain(String.valueOf(cases.get(i))),
              Doc.symbol("->")
            ),
            Doc.nest(2, Doc.vcat(stmts.view().map(AstStmt::toDoc)))));
        }
      }
      if (defaultCase.isNotEmpty()) {
        var defaultDoc = Doc.vcat(
          Doc.styled(BasePrettier.KEYWORD, "default"),
          Doc.nest(2, Doc.vcat(defaultCase.view().map(AstStmt::toDoc)))
        );
        branchesDoc.set(cases.size(), defaultDoc);
      }
      return Doc.vcat(caseDocs, Doc.nest(2, Doc.vcat(branchesDoc)));
    }
  }

  record Return(@NotNull AstVariable expr) implements AstStmt {
    @Override public @NotNull Doc toDoc() {
      return Doc.sep(Doc.styled(BasePrettier.KEYWORD, "return"), expr.toDoc());
    }
  }
}
