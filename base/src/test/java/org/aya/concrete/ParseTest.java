// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete;

import org.aya.api.Global;
import org.aya.api.error.SourcePos;
import org.aya.api.util.Arg;
import org.aya.concrete.parse.AyaParsing;
import org.aya.concrete.parse.AyaProducer;
import org.aya.pretty.doc.Doc;
import org.aya.test.ThrowingReporter;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.control.Either;
import org.glavo.kala.tuple.Tuple2;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParseTest {
  public static final @NotNull AyaProducer INSTANCE = new AyaProducer(ThrowingReporter.INSTANCE);

  @BeforeAll public static void enableTest() {
    Global.enterTestMode();
  }

  private static @NotNull Expr parseExpr(@NotNull @NonNls @Language("TEXT") String code) {
    return INSTANCE.visitExpr(AyaParsing.parser(code).expr());
  }

  private static @NotNull ImmutableSeq<Stmt> parseStmt(@NotNull @NonNls @Language("TEXT") String code) {
    return INSTANCE.visitStmt(AyaParsing.parser(code).stmt());
  }

  public static @NotNull Tuple2<Decl, ImmutableSeq<Stmt>> parseDecl(@NotNull @NonNls @Language("TEXT") String code) {
    return INSTANCE.visitDecl(AyaParsing.parser(code).decl());
  }

  @Test
  public void issue141() {
    Assertions.assertEquals(parseStmt("\\module a {}"),
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
    parseAndPretty("\\open Boy.Next.Door \\using (door) \\using (next)", """
        \\private \\open Boy::Next::Door \\using (door, next)
      """);
  }

  @Test
  public void successLiteral() {
    assertTrue(parseExpr("diavolo") instanceof Expr.UnresolvedExpr);
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
    parseFn("\\def a (t : \\Sig a b ** s) => b");
    parseFn("""
      \\def uncurry (A : \\Set) (B : \\Set) (C : \\Set)
                   (f : \\Pi A B -> C)
                   (p : \\Sig A ** B) : C
        => f p.1 p.2""");
    parseData("\\data Unit");
    parseData("\\data Unit \\abusing {}");
    parseData("\\data Unit : A \\abusing {}");
    parseData("\\data T {A : \\114-Type514} : A \\abusing {}");
    parseAndPretty("\\def id {A : \\114-Type514} (a : A) : A => a", """
        \\public \\def id {A : \\114-Type514} (a : A) : A => a
      """);
    parseAndPretty("\\def xx {A, B : \\114-Type514} (a : A) : A => a", """
        \\public \\def xx {A : \\114-Type514} {B : \\114-Type514} (a : A) : A => a
      """);
    parseAndPretty("\\data Nat | Z | S Nat", """
      \\public \\data Nat
        | Z
        | S (_ : Nat)
      """);
  }

  @Test
  public void successExpr() {
    assertTrue(parseExpr("boy") instanceof Expr.UnresolvedExpr);
    assertTrue(parseExpr("f a") instanceof Expr.AppExpr);
    assertTrue(parseExpr("f a b c") instanceof Expr.AppExpr);
    assertTrue(parseExpr("a.1") instanceof Expr.ProjExpr);
    assertTrue(parseExpr("a.1.2") instanceof Expr.ProjExpr);
    assertTrue(parseExpr("f (a.1) (a.2)") instanceof Expr.AppExpr app
      && app.arguments().get(0).term() instanceof Expr.ProjExpr
      && app.arguments().get(1).term() instanceof Expr.ProjExpr);
    assertTrue(parseExpr("λ a => a") instanceof Expr.LamExpr);
    assertTrue(parseExpr("\\lam a => a") instanceof Expr.LamExpr);
    assertTrue(parseExpr("\\lam a b => a") instanceof Expr.LamExpr);
    assertTrue(parseExpr("Π a -> a") instanceof Expr.PiExpr dt && !dt.co());
    assertTrue(parseExpr("\\Pi a -> a") instanceof Expr.PiExpr dt && !dt.co());
    assertTrue(parseExpr("\\Pi a b -> a") instanceof Expr.PiExpr dt
      && !dt.co() && dt.last() instanceof Expr.PiExpr);
    assertTrue(parseExpr("Σ a ** b") instanceof Expr.TelescopicSigmaExpr dt && !dt.co());
    assertTrue(parseExpr("\\Sig a ** b") instanceof Expr.TelescopicSigmaExpr dt && !dt.co());
    assertTrue(parseExpr("\\Sig a b ** c") instanceof Expr.TelescopicSigmaExpr dt && !dt.co());
    assertTrue(parseExpr("\\Pi (x : \\Sig a ** b) -> c") instanceof Expr.PiExpr dt && !dt.co() && dt.param().type() instanceof Expr.TelescopicSigmaExpr);
    parseTo("f a . 1", new Expr.ProjExpr(
      SourcePos.NONE,
      new Expr.AppExpr(
        SourcePos.NONE,
        new Expr.UnresolvedExpr(SourcePos.NONE, "f"),
        ImmutableSeq.of(Arg.explicit(new Expr.UnresolvedExpr(SourcePos.NONE, "a")))
      ),
      Either.left(1)
    ));
    assertTrue(parseExpr("f (a, b, c)") instanceof Expr.AppExpr app
      && app.arguments().sizeEquals(1)
      && app.arguments().get(0).term() instanceof Expr.TupExpr tup
      && tup.items().sizeEquals(3));
  }

  private void parseImport(@Language("TEXT") String code) {
    assertTrue(parseStmt(code).first() instanceof Stmt.ImportStmt s && !s.toDoc().renderWithPageWidth(114514).isEmpty());
  }

  private void parseOpen(@Language("TEXT") String code) {
    assertTrue(parseStmt(code).last() instanceof Stmt.OpenStmt s && !s.toDoc().renderWithPageWidth(114514).isEmpty());
  }

  private void parseFn(@Language("TEXT") String code) {
    assertTrue(parseDecl(code)._1 instanceof Decl.FnDecl s && !s.toDoc().renderWithPageWidth(114514).isEmpty());
  }

  private void parseData(@Language("TEXT") String code) {
    assertTrue(parseDecl(code)._1 instanceof Decl.DataDecl);
  }

  private void parseUniv(@Language("TEXT") String code) {
    assertTrue(parseExpr(code) instanceof Expr.UnivExpr);
  }

  @Test
  public void patternParseImplicit() {
    parseAndPretty(
      "\\def simple | a => a",
      "\\public \\def simple\n  | a => a"
    );
    parseAndPretty(
      "\\def unary-tuples-are-ignored | (a) => a",
      "\\public \\def unary-tuples-are-ignored\n  | a => a"
    );
    parseAndPretty(
      "\\def im-unary-tuples-are-ignored | {a} => a",
      "\\public \\def im-unary-tuples-are-ignored\n  | {a} => a"
    );
    parseAndPretty(
      "\\def we-dont-have-unary-tuples | ((((((((a)))))))) => (a)",
      "\\public \\def we-dont-have-unary-tuples\n  | a => a"
    );
    parseAndPretty(
      "\\def we-dont-have-unary-tuples | {{{{{{{{{{a}}}}}}}}}} => a",
      "\\public \\def we-dont-have-unary-tuples\n  | {a} => a"
    );
    parseAndPretty(
      "\\def we-dont-have-unary-tuples | ((((((((a)))), b)))) => (a)",
      "\\public \\def we-dont-have-unary-tuples\n  | (a, b) => a"
    );
    parseAndPretty(
      "\\def tuples | (a,b,c) => a",
      "\\public \\def tuples\n  | (a, b, c) => a"
    );
    parseAndPretty(
      "\\def im-tuples | {a,b,c} => a",
      "\\public \\def im-tuples\n  | {a, b, c} => a"
    );
    parseAndPretty(
      "\\def tuples-with-im | (a,{b},c,d,{ef}) => ef",
      "\\public \\def tuples-with-im\n  | (a, {b}, c, d, {ef}) => ef"
    );
    parseAndPretty(
      "\\def imtuple-with-extuple | {a, (b, c, d)} => ef",
      "\\public \\def imtuple-with-extuple\n  | {a, (b, c, d)} => ef"
    );
    parseAndPretty(
      "\\def im-in-ctor | suc {N} (a) => N",
      "\\public \\def im-in-ctor\n  | suc {N} a => N"
    );
    parseAndPretty(
      "\\def im-in-ctor-nested | suc {N} (suc {M} a) => a",
      "\\public \\def im-in-ctor-nested\n  | suc {N} (suc {M} a) => a"
    );
    parseAndPretty(
      "\\def final : Nat | (suc {m} {suc x} a, fuck, {114514}) \\as Outer => a",
      "\\public \\def final : Nat\n  | (suc {m} {suc x} a, fuck, {114514}) \\as Outer => a"
    );
    parseAndPretty(
      "\\struct Very-Simple (A : \\Set) : \\Set | x : A | y : Nat",
      """
      \\public \\struct Very-Simple (A : \\Set) : \\Set
        | x : A
        | y : Nat
      """
    );
    parseAndPretty(
      """
        \\struct With-Tele (B : Nat -> \\Set) : \\Set
          | x { X : \\Set } : Nat
          | y : B zero
        """,
      """
       \\public \\struct With-Tele (B : \\Pi (_ : Nat) -> \\Set) : \\Set
         | x {X : \\Set} : Nat
         | y : B zero
       """
    );
  }

  private void parseAndPretty(@NotNull @NonNls @Language("TEXT") String code, @NotNull @NonNls @Language("TEXT") String pretty) {
    var stmt = parseStmt(code);
    assertEquals(pretty.trim(), stmt.stream()
      .map(Stmt::toDoc)
      .reduce(Doc.empty(), Doc::vcat)
      .renderWithPageWidth(114514)
      .trim());
  }

  private void parseTo(@NotNull @NonNls @Language("TEXT") String code, Expr expr) {
    Assertions.assertEquals(expr, parseExpr(code));
  }
}
