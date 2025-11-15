// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ir;

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

public sealed interface IrStmt extends Docile {
  @Override default @NotNull Doc toDoc() {
    return Doc.plain("<unimplemented pretty print>");
  }

  record DeclareVariable(
    @NotNull ClassDesc type,
    @NotNull IrVariable.Local theVar,
    @Nullable IrExpr initializer
  ) implements IrStmt {
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
               @NotNull ImmutableSeq<IrValue> superConArgs) implements IrStmt { }

  record SetVariable(@NotNull IrVariable var, @NotNull IrExpr update) implements IrStmt {
    @Override public @NotNull Doc toDoc() {
      return Doc.sep(var.toDoc(), Doc.symbol(":="), update.toDoc());
    }
  }

  record SetStaticField(@NotNull FieldRef var, @NotNull IrVariable update) implements IrStmt { }

  record SetArray(@NotNull IrVariable array, int index, @NotNull IrVariable update) implements IrStmt {
    @Override public @NotNull Doc toDoc() {
      return Doc.sep(
        Doc.cat(array.toDoc(), Doc.wrap("[", "]", Doc.plain(String.valueOf(index)))),
        Doc.symbol(":="),
        update.toDoc()
      );
    }
  }

  sealed interface Condition extends Docile {
    record IsFalse(@NotNull IrVariable var) implements Condition {
      @Override public @NotNull Doc toDoc() {
        return Doc.sep(Doc.styled(BasePrettier.KEYWORD, "unless"), var.toDoc());
      }
    }
    record IsTrue(@NotNull IrVariable var) implements Condition {
      @Override public @NotNull Doc toDoc() {
        return Doc.sep(Doc.styled(BasePrettier.KEYWORD, "if"), var.toDoc());
      }
    }
    record IsInstanceOf(@NotNull IrVariable lhs, @NotNull ClassDesc rhs,
                        @NotNull MutableValue<IrVariable.Local> asTerm) implements Condition {
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
    record IsIntEqual(@NotNull IrVariable lhs, int rhs) implements Condition {
      @Override public @NotNull Doc toDoc() {
        return Doc.sep(Doc.styled(BasePrettier.KEYWORD, "if int eq"), lhs.toDoc(), Doc.plain(String.valueOf(rhs)));
      }
    }
    record IsRefEqual(@NotNull IrVariable lhs, @NotNull IrVariable rhs) implements Condition {
      @Override public @NotNull Doc toDoc() {
        return Doc.sep(Doc.styled(BasePrettier.KEYWORD, "if ref eq"), lhs.toDoc(), rhs.toDoc());
      }
    }
    record IsNull(@NotNull IrVariable ref) implements Condition {
      @Override public @NotNull Doc toDoc() {
        return Doc.sep(Doc.styled(BasePrettier.KEYWORD, "if null"), ref.toDoc());
      }
    }
  }

  record IfThenElse(@NotNull Condition cond, @NotNull ImmutableSeq<IrStmt> thenBlock,
                    @Nullable ImmutableSeq<IrStmt> elseBlock) implements IrStmt {
    @Override public @NotNull Doc toDoc() {
      var list = MutableList.of(
        cond.toDoc(),
        Doc.nest(2, Doc.vcat(thenBlock.view().map(IrStmt::toDoc))));
      if (elseBlock != null && elseBlock.isNotEmpty()) {
        list.append(Doc.styled(BasePrettier.KEYWORD, "else"));
        list.append(Doc.nest(2, Doc.vcat(elseBlock.view().map(IrStmt::toDoc))));
      }
      return Doc.vcat(list);
    }
  }

  record Breakable(@NotNull ImmutableSeq<IrStmt> block) implements IrStmt {
    @Override public @NotNull Doc toDoc() {
      return Doc.vcat(
        Doc.styled(BasePrettier.KEYWORD, "breakable"),
        Doc.nest(2, Doc.vcat(block.view().map(IrStmt::toDoc)))
      );
    }
  }

  record WhileTrue(@NotNull ImmutableSeq<IrStmt> block) implements IrStmt {
    @Override public @NotNull Doc toDoc() {
      return Doc.vcat(
        Doc.styled(BasePrettier.KEYWORD, "loop"),
        Doc.nest(2, Doc.vcat(block.view().map(IrStmt::toDoc)))
      );
    }
  }

  enum SingletonStmt implements IrStmt {
    Break,
    Continue,
    Unreachable;

    @Override public @NotNull Doc toDoc() {
      return Doc.styled(BasePrettier.KEYWORD, name().toLowerCase(Locale.ROOT));
    }
  }

  record Exec(@NotNull IrExpr expr) implements IrStmt {
    @Override public @NotNull Doc toDoc() {
      return expr.toDoc();
    }
  }

  record Switch(@NotNull IrVariable elim, @NotNull ImmutableIntSeq cases,
                @NotNull ImmutableSeq<ImmutableSeq<IrStmt>> branch,
                @NotNull ImmutableSeq<IrStmt> defaultCase) implements IrStmt {
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
            Doc.nest(2, Doc.vcat(stmts.view().map(IrStmt::toDoc)))));
        }
      }
      if (defaultCase.isNotEmpty()) {
        var defaultDoc = Doc.vcat(
          Doc.styled(BasePrettier.KEYWORD, "default"),
          Doc.nest(2, Doc.vcat(defaultCase.view().map(IrStmt::toDoc)))
        );
        branchesDoc.set(cases.size(), defaultDoc);
      }
      return Doc.vcat(caseDocs, Doc.nest(2, Doc.vcat(branchesDoc)));
    }
  }

  record Return(@NotNull IrValue expr) implements IrStmt {
    @Override public @NotNull Doc toDoc() {
      return Doc.sep(Doc.styled(BasePrettier.KEYWORD, "return"), expr.toDoc());
    }
  }
}
