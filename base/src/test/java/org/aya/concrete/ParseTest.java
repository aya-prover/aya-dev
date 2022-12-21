// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import kala.control.Option;
import org.aya.cli.parse.AyaParserImpl;
import org.aya.concrete.stmt.Command;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Stmt;
import org.aya.concrete.stmt.TeleDecl;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.pretty.doc.Doc;
import org.aya.test.AyaThrowingReporter;
import org.aya.util.error.Global;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourcePos;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("UnknownLanguage")
public class ParseTest {
  @BeforeAll public static void enableTest() {
    Global.NO_RANDOM_NAME = true;
    Global.UNITE_SOURCE_POS = true;
  }

  @AfterAll public static void exit() {
    Global.reset();
  }

  public static @NotNull Expr parseExpr(@NotNull @NonNls @Language("Aya") String code) {
    return new AyaParserImpl(AyaThrowingReporter.INSTANCE).expr(code, SourcePos.NONE);
  }

  public static @NotNull ImmutableSeq<Stmt> parseStmt(@NotNull @NonNls @Language("Aya") String code) {
    var file = new SourceFile("main.aya", Option.none(), code);
    return new AyaParserImpl(AyaThrowingReporter.INSTANCE).program(file);
  }

  public static @NotNull ImmutableSeq<Decl> parseDecl(@NotNull @NonNls @Language("Aya") String code) {
    return parseStmt(code).filterIsInstance(Decl.class);
  }

  @Test public void issue141() {
    Assertions.assertEquals(parseStmt("module a {}"),
      ImmutableSeq.of(new Command.Module(SourcePos.NONE, SourcePos.NONE, "a", ImmutableSeq.empty())));
  }

  @Test public void successCmd() {
    parseOpen("open A");
    parseOpen("open A::B");
    parseOpen("open A using ()");
    parseOpen("open A hiding ()");
    parseImport("import A");
    parseImport("import A::B");
    parseImport("open import A::B using ()");
    parseAndPretty("open Boy::Next::Door using (door) (next)", """
        open Boy::Next::Door using (door, next)
      """);
  }

  @Test public void successLiteral() {
    assertTrue(parseExpr("diavolo") instanceof Expr.Unresolved);
    assertTrue(parseExpr("Type") instanceof Expr.RawSort);
  }

  @Test public void successLift() {
    assertTrue(parseExpr("\u2191 Type") instanceof Expr.Lift lift && lift.lift() == 1);
    assertTrue(parseExpr("\u2191\u2191 Type") instanceof Expr.Lift lift && lift.lift() == 2);
  }

  @Test public void successDecl() {
    parseFn("def a => 1");
    parseFn("def a (b : X) => b");
    parseFn("def a (f : Pi a b c d -> a) => b");
    parseFn("def a (t : Sig a b ** s) => b");
    parseFn("""
      def uncurry (A : Type) (B : Type) (C : Type)
                   (f : Pi A B -> C)
                   (p : Sig A ** B) : C
        => f (p.1) (p.2)""");
    parseData("data Unit");
    parseData("data Unit : A");
    parseData("data T {A : Type} : A");
    parseAndPretty("def id {A : Type} (a : A) : A => a", """
        def id {A : Type} (a : A) : A => a
      """);
    parseAndPretty("def xx {A B : Type} (a : A) : A => a", """
        def xx {A B : Type} (a : A) : A => a
      """);
  }

  @Test public void nat() {
    parseAndPretty("data Nat | Z | S Nat", """
      data Nat
        | Z
        | S (_ : Nat)
      """);
  }

  @Test public void newPat() {
    Expr expr = parseExpr("f (a.1) (a.2)");
    assertTrue(expr instanceof Expr.BinOpSeq app
      && app.seq().get(1).term() instanceof Expr.BinOpSeq proj1
      && proj1.seq().sizeEquals(1)
      && proj1.seq().get(0).term() instanceof Expr.Proj
      && app.seq().get(2).term() instanceof Expr.BinOpSeq proj2
      && proj2.seq().sizeEquals(1)
      && proj2.seq().get(0).term() instanceof Expr.Proj);
  }

  @Test public void successExpr() {
    assertTrue(parseExpr("boy") instanceof Expr.Unresolved);
    assertTrue(parseExpr("f a") instanceof Expr.BinOpSeq);
    assertTrue(parseExpr("f a b c") instanceof Expr.BinOpSeq);
    assertTrue(parseExpr("a.1") instanceof Expr.Proj);
    assertTrue(parseExpr("a.1.2") instanceof Expr.Proj);
    assertTrue(parseExpr("f a.1") instanceof Expr.BinOpSeq app
      && app.seq().get(1).term() instanceof Expr.Proj);
    assertTrue(parseExpr("(f a).1") instanceof Expr.Proj proj
      && proj.tup() instanceof Expr.BinOpSeq);
    assertTrue(parseExpr("λ a => a") instanceof Expr.Lambda);
    assertTrue(parseExpr("\\ a => a") instanceof Expr.Lambda);
    assertTrue(parseExpr("\\ a b => a") instanceof Expr.Lambda);
    assertTrue(parseExpr("Π a -> a") instanceof Expr.Pi dt);
    assertTrue(parseExpr("Pi a -> a") instanceof Expr.Pi dt);
    assertTrue(parseExpr("Pi a b -> a") instanceof Expr.Pi dt
      && dt.last() instanceof Expr.Pi);
    assertTrue(parseExpr("Σ a ** b") instanceof Expr.Sigma dt);
    assertTrue(parseExpr("Sig a ** b") instanceof Expr.Sigma dt);
    assertTrue(parseExpr("Sig a b ** c") instanceof Expr.Sigma dt);
    assertTrue(parseExpr("Pi (x : Sig a ** b) -> c") instanceof Expr.Pi dt && dt.param().type() instanceof Expr.Sigma);
    parseTo("(f a) . 1", new Expr.Proj(
      SourcePos.NONE,
      new Expr.BinOpSeq(
        SourcePos.NONE,
        ImmutableSeq.of(
          new Expr.NamedArg(true, new Expr.BinOpSeq(
            SourcePos.NONE,
            ImmutableSeq.of(
              new Expr.NamedArg(true, new Expr.Unresolved(SourcePos.NONE, "f")),
              new Expr.NamedArg(true, new Expr.Unresolved(SourcePos.NONE, "a"))
            ))))),
      Either.left(1)
    ));
    parseTo("f a . 1", new Expr.BinOpSeq(
        SourcePos.NONE,
        ImmutableSeq.of(
          new Expr.NamedArg(true, new Expr.Unresolved(SourcePos.NONE, "f")),
          new Expr.NamedArg(true,
            new Expr.Proj(SourcePos.NONE, new Expr.Unresolved(SourcePos.NONE, "a"),
              Either.left(1))))
      )
    );
    assertTrue(parseExpr("f (a, b, c)") instanceof Expr.BinOpSeq app
      && app.seq().sizeEquals(2)
      && !app.toDoc(AyaPrettierOptions.debug()).debugRender().isEmpty()
      && app.seq().get(1).term() instanceof Expr.Tuple tup
      && tup.items().sizeEquals(3));
    assertTrue(parseExpr("new Pair A B { | fst => a | snd => b }") instanceof Expr.New neo
      && !neo.toDoc(AyaPrettierOptions.debug()).debugRender().isEmpty());
  }

  private void parseImport(@Language("Aya") String code) {
    assertTrue(parseStmt(code).first() instanceof Command.Import s && !s.toDoc(AyaPrettierOptions.debug()).debugRender().isEmpty());
  }

  private void parseOpen(@Language("Aya") String code) {
    assertTrue(parseStmt(code).last() instanceof Command.Open s && !s.toDoc(AyaPrettierOptions.debug()).debugRender().isEmpty());
  }

  private void parseFn(@Language("Aya") String code) {
    assertTrue(parseDecl(code).first() instanceof TeleDecl.FnDecl s && !s.toDoc(AyaPrettierOptions.debug()).debugRender().isEmpty());
  }

  private void parseData(@Language("Aya") String code) {
    assertTrue(parseDecl(code).first() instanceof TeleDecl.DataDecl);
  }

  @Test
  public void patternParseImplicit() {
    parseAndPretty(
      "def simple | a => a",
      "def simple\n  | a => a"
    );
    parseAndPretty(
      "def unary-tuples-are-ignored | (a) => a",
      "def unary-tuples-are-ignored\n  | a => a"
    );
    parseAndPretty(
      "def im-unary-tuples-are-ignored | {a} => a",
      "def im-unary-tuples-are-ignored\n  | {a} => a"
    );
    parseAndPretty(
      "def we-dont-have-unary-tuples | ((((((((a)))))))) => (a)",
      "def we-dont-have-unary-tuples\n  | a => a"
    );
    parseAndPretty(
      "def we-dont-have-unary-tuples | {{{{{{{{{{a}}}}}}}}}} => a",
      "def we-dont-have-unary-tuples\n  | {a} => a"
    );
    parseAndPretty(
      "def we-dont-have-unary-tuples | ((((((((a)))), b)))) => (a)",
      "def we-dont-have-unary-tuples\n  | (a, b) => a"
    );
    parseAndPretty(
      "def tuples | (a,b,c) => a",
      "def tuples\n  | (a, b, c) => a"
    );
    parseAndPretty(
      "def im-tuples | {a,b,c} => a",
      "def im-tuples\n  | {a, b, c} => a"
    );
    parseAndPretty(
      "def tuples-with-im | (a,{b},c,d,{ef}) => ef",
      "def tuples-with-im\n  | (a, {b}, c, d, {ef}) => ef"
    );
    parseAndPretty(
      "def imtuple-with-extuple | {a, (b, c, d)} => ef",
      "def imtuple-with-extuple\n  | {a, (b, c, d)} => ef"
    );
    parseAndPretty(
      "def im-in-ctor | suc {N} (a) => N",
      "def im-in-ctor\n  | suc {N} a => N"
    );
    parseAndPretty(
      "def im-in-ctor-nested | suc {N} (suc {M} a) => a",
      "def im-in-ctor-nested\n  | suc {N} (suc {M} a) => a"
    );
    parseAndPretty(
      "struct Very-Simple (A : Type) : Type | x : A | y : Nat",
      """
        struct Very-Simple (A : Type) : Type
          | x : A
          | y : Nat
        """
    );
    parseAndPretty(
      """
        struct With-Tele (B : Nat -> Type) : Type
          | x { X : Type } : Nat
          | y : B zero
        """,
      """
        struct With-Tele (B : Nat -> Type) : Type
          | x {X : Type} : Nat
          | y : B zero
        """
    );
  }

  @Test public void parseStructs() {
    parseAndPretty(
      "struct Very-Simple (A : Type) : Type | x : A => zero",
      """
        struct Very-Simple (A : Type) : Type
          | x : A => zero
        """
    );
  }

  @Test public void modules() {
    parseAndPretty("""
      module Nat {
       open data ℕ : Type | zero | suc ℕ
      }
      """, """
      module Nat {
        data ℕ : Type
          | zero
          | suc (_ : ℕ)
        public open ℕ hiding ()
      }""");
  }

  @Test public void patterns() {
    parseAndPretty("def inline final : Nat => a",
      "def inline final : Nat => a");
    parseAndPretty("def opaque final : Nat | impossible",
      "def opaque final : Nat\n  | impossible");
  }

  @Test public void exprAndCounterexamples() {
    parseAndPretty("example def test => Type",
      "example def test => Type");
    parseAndPretty("counterexample def test => {? Type ?}",
      "counterexample def test => {? Type ?}");
  }

  @Test public void issue350() {
    parseAndPretty("""
        def l : Type => \\ i => Nat
        """,
      """
        def l : Type => \\ (i : _) => Nat
        """);
    parseAndPretty("""
        def l : Type => \\ (i : I) => Nat
        """,
      """
        def l : Type => \\ (i : I) => Nat
        """);
  }

  @Test public void array() {
    parseAndPretty("""
      def list => [ 1,2 ,3 ]
      def listGen => [ x + y | x <-   xs ,y <- ys ]
      """, """
      def list => [ 1, 2, 3 ]
      def listGen => [ x + y | x <- xs, y <- ys ]
      """);
  }

  @Test public void doExpr() {
    // Testing pretty but not parse! 😂
    parseAndPretty("def foo => do { x <- xs, y <- ys, return (x * y) }",
      "def foo => do { x <- xs, y <- ys, return (x * y) }");
    parseAndPretty("""
      def foo => do
        x <- xs, y <- ys,
        return (x * y)
      """, "def foo => do { x <- xs, y <- ys, return (x * y) }");
    parseAndPretty("""
      def foo => do {
        x <- xs,
        y <- ys,
        return (x * y)
      }
      """, "def foo => do { x <- xs, y <- ys, return (x * y) }");
    parseAndPretty("""
      def foo => do {
        x <- f xs,
        y <- ff ys,
        someFuncIgnored x,
        z <- fff zs,
        return (x * y * z)
      }
      """, """
      def foo => do {
        x <- f xs,
        y <- ff ys,
        someFuncIgnored x,
        z <- fff zs,
        return (x * y * z)
      }""");
  }

  public static void parseAndPretty(@NotNull @NonNls @Language("Aya") String code, @NotNull @NonNls @Language("Aya") String pretty) {
    var stmt = parseStmt(code);
    assertEquals(pretty.trim(), Doc.vcat(stmt.view()
        .map(s -> s.toDoc(AyaPrettierOptions.debug())))
      .renderToString(80, false)
      .trim());
  }

  private void parseTo(@NotNull @NonNls @Language("Aya") String code, Expr expr) {
    Assertions.assertEquals(expr, parseExpr(code));
  }
}
