// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck;

import org.junit.jupiter.api.Test;
import org.mzi.api.error.SourcePos;
import org.mzi.concrete.Expr;
import org.mzi.concrete.Param;
import org.mzi.ref.LocalVar;
import org.mzi.test.Lisp;
import org.mzi.test.ThrowingReporter;

import static asia.kala.collection.mutable.Buffer.of;

/**
 * Test if the tycker is functioning, say, working for simple cases.
 *
 * @author ice1000
 */
public class TyckFnTest {
  @Test
  public void idLam() {
    var a = new LocalVar("a");
    // \A.\a.a
    var lamAaa = new Expr.LamExpr(SourcePos.NONE,
      of(
        new Param(SourcePos.NONE, of(() -> "_"), true),
        new Param(SourcePos.NONE, of(a), true)),
      new Expr.RefExpr(SourcePos.NONE, a));
    var piUAA = Lisp.reallyParse("(Pi (A (U) ex (a A ex null)) A)");
    var result = lamAaa.accept(new ExprTycker(ThrowingReporter.INSTANCE), piUAA);
    System.out.println(result);
  }
}
