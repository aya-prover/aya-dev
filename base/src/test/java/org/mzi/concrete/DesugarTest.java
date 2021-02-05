// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete;

import org.glavo.kala.collection.mutable.Buffer;
import org.junit.jupiter.api.Test;
import org.mzi.api.error.SourcePos;
import org.mzi.ref.LocalVar;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DesugarTest {
  @Test
  void desugarNestedLam() {
    var p2Var = new LocalVar("p2");
    var p1 = new Param(SourcePos.NONE, new LocalVar("p1"), false);
    var p2 = new Param(SourcePos.NONE, p2Var, false);
    var p3 = new Param(SourcePos.NONE, new LocalVar("p3"), true);
    var teleLam = new Expr.TelescopicLamExpr(SourcePos.NONE, Buffer.of(p1, p2, p3),
      new Expr.TelescopicLamExpr(SourcePos.NONE, Buffer.of(p1), new Expr.RefExpr(SourcePos.NONE, p2Var)));
    var lam = new Expr.LamExpr(SourcePos.NONE, p1,
      new Expr.LamExpr(SourcePos.NONE, p2,
        new Expr.LamExpr(SourcePos.NONE, p3,
          new Expr.LamExpr(SourcePos.NONE, p1,
            new Expr.RefExpr(SourcePos.NONE, p2Var)))));
    assertEquals(teleLam.desugar(), lam);
  }

  @Test
  void desugarNestedPi() {
    var p2Var = new LocalVar("p2");
    var p1 = new Param(SourcePos.NONE, new LocalVar("p1"), false);
    var p2 = new Param(SourcePos.NONE, p2Var, false);
    var p3 = new Param(SourcePos.NONE, new LocalVar("p3"), true);
    var telePi = new Expr.TelescopicPiExpr(SourcePos.NONE, true, Buffer.of(p1, p2, p3),
      new Expr.TelescopicPiExpr(SourcePos.NONE, false, Buffer.of(p1), new Expr.RefExpr(SourcePos.NONE, p2Var)));
    var pi = new Expr.PiExpr(SourcePos.NONE, true, p1,
      new Expr.PiExpr(SourcePos.NONE, true, p2,
        new Expr.PiExpr(SourcePos.NONE, true, p3,
          new Expr.PiExpr(SourcePos.NONE, false, p1,
            new Expr.RefExpr(SourcePos.NONE, p2Var)))));
    assertEquals(telePi.desugar(), pi);
  }
}
