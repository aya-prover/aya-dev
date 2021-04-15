// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.desugar;

import org.aya.api.error.ExprProblem;
import org.aya.api.error.Reporter;
import org.aya.concrete.Expr;
import org.aya.concrete.Stmt;
import org.aya.concrete.visitor.StmtFixpoint;
import org.aya.pretty.doc.Doc;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

public record Desugarer(@NotNull Reporter reporter, BinOpSet opSet) implements StmtFixpoint {
  @Override public @NotNull Expr visitBinOpSeq(@NotNull Expr.BinOpSeq binOpSeq, Unit unit) {
    var seq = binOpSeq.seq();
    assert seq.isNotEmpty() : binOpSeq.sourcePos().toString();
    return new BinOpParser(opSet, binOpSeq.seq().view())
      .build(binOpSeq.sourcePos())
      .accept(this, Unit.unit());
  }

  @Override public Unit visitBind(Stmt.@NotNull BindStmt bind, Unit unit) {
    return unit;
  }

  public static record WrongLevelError(@NotNull Expr.AppExpr expr, int expected) implements ExprProblem {
    @Override public @NotNull Doc describe() {
      return Doc.hcat(
        Doc.plain("Expected " + expected + " level(s)")
      );
    }

    @Override public @NotNull Severity level() {
      return Severity.ERROR;
    }
  }
}
