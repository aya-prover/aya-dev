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
      open data Nat
      | O | S Nat
      
      def add Nat Nat : Nat
      | n, 0 => n
      | S n, m => S (add n m)
      """;

    highlightAndTest(code,
      keyword(0, 3, "open"),
      whatever(),
      def(10, 12, "Nat", "nat", HighlightInfo.DefKind.Data),
      whatever(), // |
      def(16, 16, "O", "zero", HighlightInfo.DefKind.Con),
      whatever(), // |
      def(20, 20, "S", "suc", HighlightInfo.DefKind.Con),
      ref(22, 24, "Nat", "nat", HighlightInfo.DefKind.Data),
      whatever(),   // def
      def(31, 33, "add", HighlightInfo.DefKind.Fn),
      ref(35, 37, "Nat", "nat", HighlightInfo.DefKind.Data),
      ref(39, 41, "Nat", "nat", HighlightInfo.DefKind.Data),
      whatever(), // :
      ref(45, 47, "Nat", "nat", HighlightInfo.DefKind.Data),
      whatever(), // |
      localDef(51, 51, "n", "n'"),
      litInt(54, 54, 0),
      whatever(), // =>
      localRef(59, 59, "n", "n'"),
      whatever(), // |
      ref(63, 63, "S", "suc", HighlightInfo.DefKind.Con),
      // Note: n' is still available here, so use another name
      localDef(65, 65, "n", "n''"),
      localDef(68, 68, "m", "m'"),
      whatever(), // =>
      ref(73, 73, "S", "suc", HighlightInfo.DefKind.Con),
      whatever(), // (
      ref(76, 78, "add", HighlightInfo.DefKind.Fn),
      localRef(80, 80, "n", "n''"),
      localRef(82, 82, "m", "m'"),
      whatever() // )
    );
  }

  @Test public void unformatTest() {
    @Language("Aya") String code = """
         open
      data  Nat |
      O | S Nat
      """;

    highlightAndTest(code,
      keyword(3, 6, "open"),
      keyword(8, 11, "data"),
      def(14, 16, "Nat", "nat", HighlightInfo.DefKind.Data),
      whatever(),
      def(20, 20, "O", HighlightInfo.DefKind.Con),
      whatever(),
      def(24, 24, "S", HighlightInfo.DefKind.Con),
      ref(26, 28, "Nat", "nat", HighlightInfo.DefKind.Data));
  }

  @Test public void incorrectTest() {
    assertThrows(Throwable.class, () -> {
      @Language("Aya") String code = """
        open data List (A : Type)
        | nil
        | cons A (List A)
        """;

      highlightAndTest(code,
        whatever(),
        whatever(),
        def(10, 13, "L1st", "list", HighlightInfo.DefKind.Data));
    });
  }

  @Test public void module() {
    @Language("Aya") String code = """
      module X {}
      open X
      open data Y
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
      open data Either (A B : Type)
      | Left A
      | Right B
      
      def constA {A : Type} (a b : A) : A => a
      """;

    highlightAndTest(code,
      keyword(0, 3, "open"),
      keyword(5, 8, "data"),
      def(10, 15, "Either", "DefEither", HighlightInfo.DefKind.Data),
      whatever(), // (
      localDef(18, 18, "A", "LocalA"),
      localDef(20, 20, "B", "LocalB"),
      whatever(), // :
      keyword(24, 27, "Type"),
      whatever(), // )
      whatever(), // |
      def(32, 35, "Left", HighlightInfo.DefKind.Con),
      localRef(37, 37, "A", "LocalA"),
      whatever(), // |
      def(41, 45, "Right", HighlightInfo.DefKind.Con),
      localRef(47, 47, "B", "LocalB"),

      keyword(50, 52, "def"),
      def(54, 59, "constA", HighlightInfo.DefKind.Fn),
      whatever(), // {
      localDef(62, 62, "A", "LocalA'"),
      whatever(), // :
      keyword(66, 69, "Type"),
      whatever(), // }
      whatever(), // (
      localDef(73, 73, "a", "Locala"),
      localDef(75, 75, "b"),
      whatever(), // :
      localRef(79, 79, "A", "LocalA'"),
      whatever(), // )
      whatever(), // :
      localRef(84, 84, "A", "LocalA'"),
      whatever(), // =>
      localRef(89, 89, "a", "Locala"));
  }

  @Test public void variables() {
    @Language("Aya") String code = """
      variable A : Type
      
      def id (a : A) : A => a
      """;

    highlightAndTest(code,
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
