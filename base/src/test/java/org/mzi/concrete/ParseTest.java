// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete;

import asia.kala.collection.immutable.ImmutableList;
import asia.kala.collection.mutable.Buffer;
import org.junit.jupiter.api.Test;
import org.mzi.api.error.SourcePos;
import org.mzi.concrete.parse.MziProducer;
import org.mzi.generic.Modifier;
import org.mzi.ref.LocalVar;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    parseTo("\\open Boy.Next.Door \\hiding (boy) \\using (door)", new Stmt.CmdStmt(
      SourcePos.NONE,
      Stmt.CmdStmt.Cmd.Open,
      "Boy.Next.Door",
      ImmutableList.of("boy"),
      ImmutableList.of("door")
    ));
  }

  @Test
  public void successLiteral() {
    assertTrue(MziProducer.parseExpr("diavolo") instanceof Expr.UnresolvedExpr);
    assertTrue(MziProducer.parseExpr("\\Prop") instanceof Expr.UnivExpr);
    assertTrue(MziProducer.parseExpr("\\Set") instanceof Expr.UnivExpr);
    assertTrue(MziProducer.parseExpr("\\Set0") instanceof Expr.UnivExpr);
    assertTrue(MziProducer.parseExpr("\\Set233") instanceof Expr.UnivExpr);
    assertTrue(MziProducer.parseExpr("\\2-Type") instanceof Expr.UnivExpr);
    assertTrue(MziProducer.parseExpr("\\2-Type2") instanceof Expr.UnivExpr);
    assertTrue(MziProducer.parseExpr("\\114-Type514") instanceof Expr.UnivExpr);
    assertTrue(MziProducer.parseExpr("\\hType2") instanceof Expr.UnivExpr);
    assertTrue(MziProducer.parseExpr("\\h-Type2") instanceof Expr.UnivExpr);
    assertTrue(MziProducer.parseExpr("\\oo-Type2") instanceof Expr.UnivExpr);
  }

  @Test
  public void successDecl() {
    assertTrue(MziProducer.parseDecl("\\def a => 1") instanceof Decl.FnDecl);
    assertTrue(MziProducer.parseDecl("\\data Unit") instanceof Decl.DataDecl);
    parseTo("\\def id {A : \\Set0} (a : A) : A => a", new Decl.FnDecl(
      SourcePos.NONE,
      EnumSet.noneOf(Modifier.class),
      null,
      "id",
      Buffer.of(
        new Param(SourcePos.NONE, Buffer.of(new LocalVar("A")), new Expr.UnivExpr(SourcePos.NONE, 0, 0), false),
        new Param(SourcePos.NONE, Buffer.of(new LocalVar("a")), new Expr.UnresolvedExpr(SourcePos.NONE, "A"), true)
      ),
      new Expr.UnresolvedExpr(SourcePos.NONE, "A"),
      new Expr.UnresolvedExpr(SourcePos.NONE, "a"),
      Buffer.of()
    ));
  }

  private void parseTo(String code, Stmt stmt) {
    assertEquals(stmt, MziProducer.parseStmt(code));
  }

  private void parseTo(String code, Expr expr) {
    assertEquals(expr, MziProducer.parseExpr(code));
  }

  private void parseTo(String code, Decl decl) {
    assertEquals(decl, MziProducer.parseDecl(code));
  }
}
