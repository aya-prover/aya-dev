// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete;

import asia.kala.collection.immutable.ImmutableList;
import asia.kala.collection.immutable.ImmutableSeq;
import asia.kala.collection.mutable.Buffer;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mzi.api.Global;
import org.mzi.api.error.SourcePos;
import org.mzi.api.util.DTKind;
import org.mzi.concrete.parse.MziProducer;
import org.mzi.generic.Arg;
import org.mzi.generic.Modifier;
import org.mzi.ref.LocalVar;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParseTest {
  @Test
  public void successCmd() {
    Global.runInTestMode(() -> {
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
        false,
        Stmt.CmdStmt.Cmd.Open,
        "Boy.Next.Door",
        ImmutableList.of("door"),
        ImmutableList.of("boy")
      ));
    });
  }

  @Test
  public void successLiteral() {
    Global.runInTestMode(() -> {
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
    });
  }

  @Test
  public void successDecl() {
    Global.runInTestMode(() -> {
      assertTrue(MziProducer.parseDecl("\\def a => 1") instanceof Decl.FnDecl);
      assertTrue(MziProducer.parseDecl("\\def a (b : X) => b") instanceof Decl.FnDecl);
      assertTrue(MziProducer.parseDecl("\\def a (f : \\Pi a b c d -> a) => b") instanceof Decl.FnDecl);
      assertTrue(MziProducer.parseDecl("\\def a (t : \\Sigma a b ** s) => b") instanceof Decl.FnDecl);
      assertTrue(MziProducer.parseDecl("\\data Unit") instanceof Decl.DataDecl);
      assertTrue(MziProducer.parseDecl("\\data Unit \\abusing {}") instanceof Decl.DataDecl);
      assertTrue(MziProducer.parseDecl("\\data Unit : A \\abusing {}") instanceof Decl.DataDecl);
      assertTrue(MziProducer.parseDecl("\\data T {A : \\114-Type514} : A \\abusing {}") instanceof Decl.DataDecl);
      parseTo("\\def id {A : \\114-Type514} (a : A) : A => a", new Decl.FnDecl(
        SourcePos.NONE,
        false,
        EnumSet.noneOf(Modifier.class),
        null,
        "id",
        Buffer.of(
          new Param(SourcePos.NONE, Buffer.of(new LocalVar("A")), new Expr.UnivExpr(SourcePos.NONE, 514, 114), false),
          new Param(SourcePos.NONE, Buffer.of(new LocalVar("a")), new Expr.UnresolvedExpr(SourcePos.NONE, "A"), true)
        ),
        new Expr.UnresolvedExpr(SourcePos.NONE, "A"),
        new Expr.UnresolvedExpr(SourcePos.NONE, "a"),
        Buffer.of()
      ));
      parseTo("\\public \\data Nat | Z | S Nat", new Decl.DataDecl(
        SourcePos.NONE,
        true,
        "Nat",
        Buffer.of(),
        new Expr.HoleExpr(SourcePos.NONE, null, null),
        new Decl.DataBody.Ctors(Buffer.of(
          new Decl.DataCtor("Z", Buffer.of(), Buffer.of(), Buffer.of(), false),
          new Decl.DataCtor("S",
            Buffer.of(
              new Param(SourcePos.NONE, Buffer.of(new LocalVar("_")), new Expr.UnresolvedExpr(SourcePos.NONE, "Nat"), true)
            ),
            Buffer.of(), Buffer.of(), false
          )
        )),
        Buffer.of()
      ));
    });
  }

  @Test
  public void successExpr() {
    Global.runInTestMode(() -> {
      assertTrue(MziProducer.parseExpr("boy") instanceof Expr.UnresolvedExpr);
      assertTrue(MziProducer.parseExpr("f a") instanceof Expr.AppExpr);
      assertTrue(MziProducer.parseExpr("f a b c") instanceof Expr.AppExpr);
      assertTrue(MziProducer.parseExpr("a.1") instanceof Expr.ProjExpr);
      assertTrue(MziProducer.parseExpr("a.1.2") instanceof Expr.ProjExpr);
      assertTrue(MziProducer.parseExpr("λ a => a") instanceof Expr.LamExpr);
      assertTrue(MziProducer.parseExpr("\\lam a => a") instanceof Expr.LamExpr);
      assertTrue(MziProducer.parseExpr("\\lam a b => a") instanceof Expr.LamExpr);
      assertTrue(MziProducer.parseExpr("Π a -> a") instanceof Expr.DTExpr dt && dt.kind() == DTKind.Pi);
      assertTrue(MziProducer.parseExpr("\\Pi a -> a") instanceof Expr.DTExpr dt && dt.kind() == DTKind.Pi);
      assertTrue(MziProducer.parseExpr("\\Pi a b -> a") instanceof Expr.DTExpr dt && dt.kind() == DTKind.Pi);
      assertTrue(MziProducer.parseExpr("Σ a ** b") instanceof Expr.DTExpr dt && dt.kind() == DTKind.Sigma);
      assertTrue(MziProducer.parseExpr("\\Sig a ** b") instanceof Expr.DTExpr dt && dt.kind() == DTKind.Sigma);
      assertTrue(MziProducer.parseExpr("\\Sig a b ** c") instanceof Expr.DTExpr dt && dt.kind() == DTKind.Sigma);
      parseTo("f a . 1", new Expr.ProjExpr(
        SourcePos.NONE,
        new Expr.AppExpr(
          SourcePos.NONE,
          new Expr.UnresolvedExpr(SourcePos.NONE, "f"),
          ImmutableSeq.of(Arg.explicit(new Expr.UnresolvedExpr(SourcePos.NONE, "a")))
        ),
        1
      ));
    });
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
