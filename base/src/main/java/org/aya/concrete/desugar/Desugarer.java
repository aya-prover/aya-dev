// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.desugar;

import org.aya.concrete.Expr;
import org.aya.concrete.parse.BinOpParser;
import org.aya.concrete.visitor.StmtFixpoint;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

public final class Desugarer implements StmtFixpoint {
  public static final Desugarer INSTANCE = new Desugarer();

  private Desugarer() {
  }

  @Override public @NotNull Expr visitBinOpSeq(@NotNull Expr.BinOpSeq binOpSeq, Unit unit) {
    var seq = binOpSeq.seq();
    assert seq.isNotEmpty() : binOpSeq.sourcePos().toString();
    return new BinOpParser(binOpSeq.seq().view())
      .build(binOpSeq.sourcePos())
      .accept(this, Unit.unit());
  }
}
