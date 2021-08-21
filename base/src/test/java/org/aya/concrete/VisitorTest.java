// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete;

import kala.collection.mutable.Buffer;
import kala.tuple.Unit;
import org.aya.concrete.visitor.StmtConsumer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class VisitorTest {
  @Test public void stmt() {
    var exprs = Buffer.<Expr>create();
    var visitor = new StmtConsumer<Unit>() {
      @Override public Unit visitUnresolved(@NotNull Expr.UnresolvedExpr expr, Unit unit) {
        exprs.append(expr);
        return unit;
      }
    };
    for (var stmt : ParseTest.parseManyStmt("""
      open data Nat : Set | zero | suc Nat
      def add (a b : Nat) : Nat
        | zero, zero => {??}
      prim I prim left : I prim right
      struct Path (A : I -> hType (lsuc l)) (a : A left) (b : A right) : hType l
        | at (i : I) : A i { | left => a | right => b }
      open data Int : Set
        | pos Nat
        | neg Nat { | zero => pos zero }
      def sig : Sig Nat ** Nat => (zero, suc zero)
      """)) {
      stmt.accept(visitor, Unit.unit());
    }
    assertTrue(exprs.isNotEmpty());
  }
}
