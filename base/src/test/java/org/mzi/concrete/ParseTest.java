// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete;

import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.immutable.ImmutableVector;
import org.glavo.kala.collection.mutable.Buffer;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mzi.api.Global;
import org.mzi.api.error.SourcePos;
import org.mzi.concrete.parse.MziProducer;
import org.mzi.generic.Arg;
import org.mzi.generic.Modifier;
import org.mzi.ref.LocalVar;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParseTest {
  @BeforeAll public static void enableTest() {
    Global.enterTestMode();
  }

  @Test
  public void successCmd() {
    parseCmd("\\open A");
    parseCmd("\\open A.B");
    parseCmd("\\open A \\using ()");
    parseCmd("\\open A \\hiding ()");
    parseCmd("\\import A");
    parseCmd("\\import A.B");
    parseCmd("\\import A.B \\using ()");
    parseTo("\\open Boy.Next.Door \\using (door) \\using (next)", new Stmt.CmdStmt(
      SourcePos.NONE,
      Stmt.Accessibility.Private,
      Stmt.CmdStmt.Cmd.Open,
      ImmutableSeq.of("Boy", "Next", "Door"),
      new Stmt.CmdStmt.UseHide(ImmutableVector.of("door", "next"), Stmt.CmdStmt.UseHide.Strategy.Using)
    ));
  }

  @Test
  public void successLiteral() {
    assertTrue(MziProducer.parseExpr("diavolo") instanceof Expr.UnresolvedExpr);
    parseUniv("\\Prop");
    parseUniv("\\Set");
    parseUniv("\\Set0");
    parseUniv("\\Set233");
    parseUniv("\\2-Type");
    parseUniv("\\2-Type2");
    parseUniv("\\114-Type514");
    parseUniv("\\hType2");
    parseUniv("\\h-Type2");
    parseUniv("\\oo-Type2");
  }

  @Test
  public void successDecl() {
    assertTrue(MziProducer.parseDecl("\\def a => 1") instanceof Decl.FnDecl);
    assertTrue(MziProducer.parseDecl("\\def a (b : X) => b") instanceof Decl.FnDecl);
    assertTrue(MziProducer.parseDecl("\\def a (f : \\Pi a b c d -> a) => b") instanceof Decl.FnDecl);
    assertTrue(MziProducer.parseDecl("\\def a (t : \\Sigma a b ** s) => b") instanceof Decl.FnDecl);
    assertTrue(MziProducer.parseDecl("\\data Unit") instanceof Decl.DataDecl);
    assertTrue(MziProducer.parseDecl("\\data Unit \\abusing {}") instanceof Decl.DataDecl);
    assertTrue(MziProducer.parseDecl("\\data Unit : A \\abusing {}") instanceof Decl.DataDecl);
    assertTrue(MziProducer.parseDecl("\\data T {A : \\114-Type514} : A \\abusing {}") instanceof Decl.DataDecl);
    parseTo("\\public \\def id {A : \\114-Type514} (a : A) : A => a", new Decl.FnDecl(
      SourcePos.NONE,
      Stmt.Accessibility.Public,
      EnumSet.noneOf(Modifier.class),
      null,
      "id",
      Buffer.of(
        new Param(SourcePos.NONE, new LocalVar("A"), new Expr.UnivExpr(SourcePos.NONE, 514, 114), false),
        new Param(SourcePos.NONE, new LocalVar("a"), new Expr.UnresolvedExpr(SourcePos.NONE, "A"), true)
      ),
      new Expr.UnresolvedExpr(SourcePos.NONE, "A"),
      new Expr.UnresolvedExpr(SourcePos.NONE, "a"),
      Buffer.of()
    ));
    parseTo("\\data Nat | Z | S Nat", new Decl.DataDecl(
      SourcePos.NONE,
      Stmt.Accessibility.Public,
      "Nat",
      Buffer.of(),
      new Expr.HoleExpr(SourcePos.NONE, null, null),
      new Decl.DataBody.Ctors(Buffer.of(
        new Decl.DataCtor("Z", Buffer.of(), Buffer.of(), Buffer.of(), false),
        new Decl.DataCtor("S",
          Buffer.of(
            new Param(SourcePos.NONE, new LocalVar("_"), new Expr.UnresolvedExpr(SourcePos.NONE, "Nat"), true)
          ),
          Buffer.of(), Buffer.of(), false
        )
      )),
      Buffer.of()
    ));
  }

  @Test
  public void successExpr() {
    assertTrue(MziProducer.parseExpr("boy") instanceof Expr.UnresolvedExpr);
    assertTrue(MziProducer.parseExpr("f a") instanceof Expr.AppExpr);
    assertTrue(MziProducer.parseExpr("f a b c") instanceof Expr.AppExpr);
    assertTrue(MziProducer.parseExpr("a.1") instanceof Expr.ProjExpr);
    assertTrue(MziProducer.parseExpr("a.1.2") instanceof Expr.ProjExpr);
    assertTrue(MziProducer.parseExpr("λ a => a") instanceof Expr.TelescopicLamExpr);
    assertTrue(MziProducer.parseExpr("\\lam a => a") instanceof Expr.TelescopicLamExpr);
    assertTrue(MziProducer.parseExpr("\\lam a b => a") instanceof Expr.TelescopicLamExpr);
    assertTrue(MziProducer.parseExpr("Π a -> a") instanceof Expr.TelescopicPiExpr dt && !dt.co());
    assertTrue(MziProducer.parseExpr("\\Pi a -> a") instanceof Expr.TelescopicPiExpr dt && !dt.co());
    assertTrue(MziProducer.parseExpr("\\Pi a b -> a") instanceof Expr.TelescopicPiExpr dt && !dt.co());
    assertTrue(MziProducer.parseExpr("Σ a ** b") instanceof Expr.TelescopicSigmaExpr dt && !dt.co());
    assertTrue(MziProducer.parseExpr("\\Sig a ** b") instanceof Expr.TelescopicSigmaExpr dt && !dt.co());
    assertTrue(MziProducer.parseExpr("\\Sig a b ** c") instanceof Expr.TelescopicSigmaExpr dt && !dt.co());
    parseTo("f a . 1", new Expr.ProjExpr(
      SourcePos.NONE,
      new Expr.AppExpr(
        SourcePos.NONE,
        new Expr.UnresolvedExpr(SourcePos.NONE, "f"),
        ImmutableSeq.of(Arg.explicit(new Expr.UnresolvedExpr(SourcePos.NONE, "a")))
      ),
      1
    ));
  }

  private void parseCmd(@Language("TEXT") String code) {
    assertTrue(MziProducer.parseStmt(code) instanceof Stmt.CmdStmt);
  }

  private void parseUniv(@Language("TEXT") String code) {
    assertTrue(MziProducer.parseExpr(code) instanceof Expr.UnivExpr);
  }

  private void parseTo(@NotNull @NonNls @Language("TEXT") String code, Stmt stmt) {
    assertEquals(stmt, MziProducer.parseStmt(code));
  }

  private void parseTo(@NotNull @NonNls @Language("TEXT") String code, Expr expr) {
    assertEquals(expr, MziProducer.parseExpr(code));
  }

  private void parseTo(@NotNull @NonNls @Language("TEXT") String code, Decl decl) {
    assertEquals(decl, MziProducer.parseDecl(code));
  }
}
