// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete;

import kala.collection.mutable.DynamicSeq;
import kala.tuple.Unit;
import org.aya.api.Global;
import org.aya.concrete.visitor.StmtConsumer;
import org.aya.core.def.PrimDef;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class VisitorTest {
  @BeforeAll public static void enableTest() {
    PrimDef.Factory.INSTANCE.clear();
    Global.NO_RANDOM_NAME = true;
    Global.UNITE_SOURCE_POS = true;
  }

  @AfterAll public static void exit() {
    PrimDef.Factory.INSTANCE.clear();
    Global.reset();
  }

  @Test public void stmt() {
    var exprs = DynamicSeq.<Expr>create();
    var visitor = new StmtConsumer<Unit>() {
      @Override public Unit visitUnresolved(@NotNull Expr.UnresolvedExpr expr, Unit unit) {
        exprs.append(expr);
        return unit;
      }
    };
    for (var stmt : ParseTest.parseManyStmt("""
      open data Nat : Type | zero | suc Nat
      def add (a b : Nat) : Nat
        | zero, zero => {??}
      prim I prim left : I prim right
      struct Path (A : I -> hType (lsuc l)) (a : A left) (b : A right) : hType l
        | at (i : I) : A i { | left => a | right => b }
      open data Int : Type
        | pos Nat
        | neg Nat { | zero => pos zero }
      def sig : Sig Nat ** Nat => (zero, suc zero)
      """)) {
      stmt.accept(visitor, Unit.unit());
    }
    assertTrue(exprs.isNotEmpty());
  }
}
