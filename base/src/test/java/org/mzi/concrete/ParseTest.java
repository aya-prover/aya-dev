// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
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
  public void issue141() {
    assertEquals(MziProducer.parseStmt("\\module a {}"),
      ImmutableSeq.of(new Stmt.ModuleStmt(SourcePos.NONE, "a", ImmutableSeq.empty())));
  }

  @Test
  public void successCmd() {
    parseOpen("\\open A");
    parseOpen("\\open A.B");
    parseOpen("\\open A \\using ()");
    parseOpen("\\open A \\hiding ()");
    parseImport("\\import A");
    parseImport("\\import A.B");
    parseImport("\\import A.B \\using ()");
    parseTo("\\open Boy.Next.Door \\using (door) \\using (next)", ImmutableSeq.of(new Stmt.OpenStmt(
      SourcePos.NONE,
      Stmt.Accessibility.Private,
      ImmutableSeq.of("Boy", "Next", "Door"),
      new Stmt.OpenStmt.UseHide(ImmutableVector.of("door", "next"), Stmt.OpenStmt.UseHide.Strategy.Using)
    )));
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
    parseFn("\\def a => 1");
    parseFn("\\def a (b : X) => b");
    parseFn("\\def a (f : \\Pi a b c d -> a) => b");
    parseFn("\\def a (t : \\Sigma a b ** s) => b");
    parseData("\\data Unit");
    parseData("\\data Unit \\abusing {}");
    parseData("\\data Unit : A \\abusing {}");
    parseData("\\data T {A : \\114-Type514} : A \\abusing {}");
    final var A = new Param(SourcePos.NONE, new LocalVar("A"), new Expr.UnivExpr(SourcePos.NONE, 514, 114), false);
    final var a = new Param(SourcePos.NONE, new LocalVar("a"), new Expr.UnresolvedExpr(SourcePos.NONE, "A"), true);
    parseTo("\\def id {A : \\114-Type514} (a : A) : A => a", ImmutableSeq.of(new Decl.FnDecl(
      SourcePos.NONE,
      Stmt.Accessibility.Public,
      EnumSet.noneOf(Modifier.class),
      null,
      "id",
      ImmutableSeq.of(A, a),
      new Expr.UnresolvedExpr(SourcePos.NONE, "A"),
      new Expr.UnresolvedExpr(SourcePos.NONE, "a"),
      Buffer.of()
    )));
    final var b = new Param(SourcePos.NONE, new LocalVar("B"), new Expr.UnivExpr(SourcePos.NONE, 514, 114), false);
    parseTo("\\def xx {A, B : \\114-Type514} (a : A) : A => a", ImmutableSeq.of(new Decl.FnDecl(
      SourcePos.NONE,
      Stmt.Accessibility.Public,
      EnumSet.noneOf(Modifier.class),
      null,
      "xx",
      ImmutableSeq.of(A, b, a),
      new Expr.UnresolvedExpr(SourcePos.NONE, "A"),
      new Expr.UnresolvedExpr(SourcePos.NONE, "a"),
      Buffer.of()
    )));
    parseTo("\\data Nat | Z | S Nat", ImmutableSeq.of(new Decl.DataDecl(
      SourcePos.NONE,
      Stmt.Accessibility.Public,
      "Nat",
      ImmutableSeq.of(),
      new Expr.HoleExpr(SourcePos.NONE, null, null),
      new Decl.DataBody.Ctors(Buffer.of(
        new Decl.DataCtor(SourcePos.NONE,"Z", ImmutableSeq.of(), Buffer.of(), Buffer.of(), false),
        new Decl.DataCtor(SourcePos.NONE,"S",
          ImmutableSeq.of(
            new Param(SourcePos.NONE, new LocalVar("_"), new Expr.UnresolvedExpr(SourcePos.NONE, "Nat"), true)
          ),
          Buffer.of(), Buffer.of(), false
        )
      )),
      Buffer.of()
    )));
  }

  @Test
  public void successExpr() {
    assertTrue(MziProducer.parseExpr("boy") instanceof Expr.UnresolvedExpr);
    assertTrue(MziProducer.parseExpr("f a") instanceof Expr.AppExpr);
    assertTrue(MziProducer.parseExpr("f a b c") instanceof Expr.AppExpr);
    assertTrue(MziProducer.parseExpr("a.1") instanceof Expr.ProjExpr);
    assertTrue(MziProducer.parseExpr("a.1.2") instanceof Expr.ProjExpr);
    assertTrue(MziProducer.parseExpr("λ a => a") instanceof Expr.LamExpr);
    assertTrue(MziProducer.parseExpr("\\lam a => a") instanceof Expr.LamExpr);
    assertTrue(MziProducer.parseExpr("\\lam a b => a") instanceof Expr.LamExpr);
    assertTrue(MziProducer.parseExpr("Π a -> a") instanceof Expr.PiExpr dt && !dt.co());
    assertTrue(MziProducer.parseExpr("\\Pi a -> a") instanceof Expr.PiExpr dt && !dt.co());
    assertTrue(MziProducer.parseExpr("\\Pi a b -> a") instanceof Expr.PiExpr dt && !dt.co());
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

  private void parseImport(@Language("TEXT") String code) {
    assertTrue(MziProducer.parseStmt(code).first() instanceof Stmt.ImportStmt);
  }

  private void parseOpen(@Language("TEXT") String code) {
    assertTrue(MziProducer.parseStmt(code).last() instanceof Stmt.OpenStmt);
  }

  private void parseFn(@Language("TEXT") String code) {
    assertTrue(MziProducer.parseDecl(code)._1 instanceof Decl.FnDecl);
  }

  private void parseData(@Language("TEXT") String code) {
    assertTrue(MziProducer.parseDecl(code)._1 instanceof Decl.DataDecl);

  }

  private void parseUniv(@Language("TEXT") String code) {
    assertTrue(MziProducer.parseExpr(code) instanceof Expr.UnivExpr);
  }

  private void parseTo(@NotNull @NonNls @Language("TEXT") String code, ImmutableSeq<Stmt> stmt) {
    assertEquals(stmt, MziProducer.parseStmt(code));
  }

  private void parseTo(@NotNull @NonNls @Language("TEXT") String code, Expr expr) {
    assertEquals(expr, MziProducer.parseExpr(code));
  }
}
