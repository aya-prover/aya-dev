// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.literate;

import org.aya.cli.literate.HighlightInfo;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.aya.test.literate.HighlighterTester.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HighlighterTest {
  @Test public void commonTests() {
    @Language("Aya") String code = """
      open inductive Nat
      | O | S Nat
      
      def add Nat Nat : Nat
      | n, 0 => n
      | S n, m => S (add n m)
      """;

    highlightAndTest("open inductive Nat\n| O | S Nat\n\ndef add Nat Nat : Nat\n| n, 0 => n\n| S n, m => S (add n m)\n",
      keyword(0, 3, "open"),
      whatever(),
      def(15, 17, "Nat", "nat", HighlightInfo.DefKind.Data),
      whatever(), // |
      def(21, 21, "O", "zero", HighlightInfo.DefKind.Con),
      whatever(), // |
      def(25, 25, "S", "suc", HighlightInfo.DefKind.Con),
      ref(27, 29, "Nat", "nat", HighlightInfo.DefKind.Data),
      whatever(),   // def
      def(36, 38, "add", HighlightInfo.DefKind.Fn),
      ref(40, 42, "Nat", "nat", HighlightInfo.DefKind.Data),
      ref(44, 46, "Nat", "nat", HighlightInfo.DefKind.Data),
      whatever(), // :
      ref(50, 52, "Nat", "nat", HighlightInfo.DefKind.Data),
      whatever(), // |
      localDef(56, 56, "n", "n'"),
      litInt(59, 59, 0),
      whatever(), // =>
      localRef(64, 64, "n", "n'"),
      whatever(), // |
      ref(68, 68, "S", "suc", HighlightInfo.DefKind.Con),
      // Note: n' is still available here, so use another name
      localDef(70, 70, "n", "n''"),
      localDef(73, 73, "m", "m'"),
      whatever(), // =>
      ref(78, 78, "S", "suc", HighlightInfo.DefKind.Con),
      whatever(), // (
      ref(81, 83, "add", HighlightInfo.DefKind.Fn),
      localRef(85, 85, "n", "n''"),
      localRef(87, 87, "m", "m'"),
      whatever() // )
    );
  }

  @Test public void unformatTest() {
    @Language("Aya") String code = """
         open
      inductive  Nat |
      O | S Nat
      """;

    highlightAndTest("   open\ninductive  Nat |\nO | S Nat\n",
      keyword(3, 6, "open"),
      keyword(8, 16, "inductive"),
      def(19, 21, "Nat", "nat", HighlightInfo.DefKind.Data),
      whatever(),
      def(25, 25, "O", HighlightInfo.DefKind.Con),
      whatever(),
      def(29, 29, "S", HighlightInfo.DefKind.Con),
      ref(31, 33, "Nat", "nat", HighlightInfo.DefKind.Data));
  }

  @Test public void incorrectTest() {
    assertThrows(Throwable.class, () -> {
      @Language("Aya") String code = """
        open inductive List (A : Type)
        | nil
        | cons A (List A)
        """;

      highlightAndTest("open inductive List (A : Type)\n| nil\n| cons A (List A)\n",
        whatever(),
        whatever(),
        def(15, 18, "L1st", "list", HighlightInfo.DefKind.Data));
    });
  }

  @Test public void module() {
    @Language("Aya") String code = """
      module X {}
      open X
      open inductive Y
      """;

    // TODO: enable this test when `SyntaxHighlight.foldModuleRef` is fixed
    // highlightAndTest(code,
    //   keyword(0, 5, "module"),
    //   def(7, 7, "X", "x", HighlightInfo.DefKind.Module),
    //   whatever(), whatever(), // {}
    //   keyword(12, 15, "open"),
    //   ref(17, 17, "X", "x", HighlightInfo.DefKind.Module),
    //   keyword(19, 22, "open"),
    //   keyword(24, 27, "data"),
    //   def(29, 29, "Y", "y", HighlightInfo.DefKind.Data));
  }

  @Test public void params() {
    @Language("Aya") String code = """
      open inductive Either (A B : Type)
      | Left A
      | Right B
      
      def constA {A : Type} (a b : A) : A => a
      """;

    highlightAndTest("open inductive Either (A B : Type)\n| Left A\n| Right B\n\ndef constA {A : Type} (a b : A) : A => a\n",
      keyword(0, 3, "open"),
      keyword(5, 13, "inductive"),
      def(15, 20, "Either", "DefEither", HighlightInfo.DefKind.Data),
      whatever(), // (
      localDef(23, 23, "A", "LocalA"),
      localDef(25, 25, "B", "LocalB"),
      whatever(), // :
      keyword(29, 32, "Type"),
      whatever(), // )
      whatever(), // |
      def(37, 40, "Left", HighlightInfo.DefKind.Con),
      localRef(42, 42, "A", "LocalA"),
      whatever(), // |
      def(46, 50, "Right", HighlightInfo.DefKind.Con),
      localRef(52, 52, "B", "LocalB"),

      keyword(55, 57, "def"),
      def(59, 64, "constA", HighlightInfo.DefKind.Fn),
      whatever(), // {
      localDef(67, 67, "A", "LocalA'"),
      whatever(), // :
      keyword(71, 74, "Type"),
      whatever(), // }
      whatever(), // (
      localDef(78, 78, "a", "Locala"),
      localDef(80, 80, "b"),
      whatever(), // :
      localRef(84, 84, "A", "LocalA'"),
      whatever(), // )
      whatever(), // :
      localRef(89, 89, "A", "LocalA'"),
      whatever(), // =>
      localRef(94, 94, "a", "Locala"));
  }

  @Test public void variables() {
    @Language("Aya") String code = """
      variable A : Type
      
      def id (a : A) : A => a
      """;

    highlightAndTest("variable A : Type\n\ndef id (a : A) : A => a\n",
      keyword(0, 7, "variable"),
      def(9, 9, "A", "GA", HighlightInfo.DefKind.Generalized),
      whatever(), // :
      keyword(13, 16, "Type"),
      keyword(19, 21, "def"),
      def(23, 24, "id", HighlightInfo.DefKind.Fn),
      whatever(), // (
      localDef(27, 27, "a", "a"),
      whatever(), // :
      ref(31, 31, "A", "GA", HighlightInfo.DefKind.Generalized),
      whatever(), // )
      whatever(), // :
      ref(36, 36, "A", "GA", HighlightInfo.DefKind.Generalized),
      whatever(), // =>
      localRef(41, 41, "a", "a"));
  }
}
