// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete;

import org.junit.jupiter.api.Test;
import org.mzi.concrete.parse.MziProducer;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParseTest {
  @Test
  public void successCmd() {
    assertTrue(MziProducer.parseStmt("\\open A") instanceof Stmt.CmdStmt);
    assertTrue(MziProducer.parseStmt("\\open A.B") instanceof Stmt.CmdStmt);
    assertTrue(MziProducer.parseStmt("\\open A \\using ()") instanceof Stmt.CmdStmt);
    assertTrue(MziProducer.parseStmt("\\open A \\using () \\hiding ()") instanceof Stmt.CmdStmt);
    assertTrue(MziProducer.parseStmt("\\open A \\hiding ()") instanceof Stmt.CmdStmt);
    assertTrue(MziProducer.parseStmt("\\open A \\hiding () \\using ()") instanceof Stmt.CmdStmt);
    assertTrue(MziProducer.parseStmt("\\import A") instanceof Stmt.CmdStmt);
    assertTrue(MziProducer.parseStmt("\\import A.B") instanceof Stmt.CmdStmt);
    assertTrue(MziProducer.parseStmt("\\import A.B \\using ()") instanceof Stmt.CmdStmt);
  }

  @Test
  public void successLiteral() {
    assertTrue(MziProducer.parseExpr("diavolo") instanceof Expr.UnresolvedExpr);
    assertTrue(MziProducer.parseExpr("\\Prop") instanceof Expr.UnivExpr);
    assertTrue(MziProducer.parseExpr("\\Set") instanceof Expr.UnivExpr);
    assertTrue(MziProducer.parseExpr("\\Set0") instanceof Expr.UnivExpr);
    // The following should be fixed
    // assertTrue(MziProducer.parseExpr("\\2-Type") instanceof Expr.UnivExpr);
    // assertTrue(MziProducer.parseExpr("\\2-Type2") instanceof Expr.UnivExpr);
    // assertTrue(MziProducer.parseExpr("\\hType2") instanceof Expr.UnivExpr);
    // assertTrue(MziProducer.parseExpr("\\h-Type2") instanceof Expr.UnivExpr);
    // assertTrue(MziProducer.parseExpr("\\oo-Type2") instanceof Expr.UnivExpr);
  }
}
