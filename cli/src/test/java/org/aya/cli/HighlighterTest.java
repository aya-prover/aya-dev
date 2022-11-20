// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli;

import org.aya.cli.literate.HighlightInfo;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.aya.cli.HighlighterTester.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("UnknownLanguage")
public class HighlighterTest {
  @Test
  public void commonTests() {
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
      def(16, 16, "O", "zero", HighlightInfo.DefKind.Con),
      def(20, 20, "S", "suc", HighlightInfo.DefKind.Con),
      ref(22, 24, "Nat", "nat", HighlightInfo.DefKind.Data),
      whatever(),   // def
      def(31, 33, "add", HighlightInfo.DefKind.Fn),
      ref(35, 37, "Nat", "nat", HighlightInfo.DefKind.Data),
      ref(39, 41, "Nat", "nat", HighlightInfo.DefKind.Data),
      ref(45, 47, "Nat", "nat", HighlightInfo.DefKind.Data),
      localDef(51, 51, "n", "n'"),
      litInt(54, 54, 0),
      localRef(59, 59, "n", "n'"),
      ref(63, 63, "S", "suc", HighlightInfo.DefKind.Con),
      // Note: n' is still available here, so use another name
      localDef(65, 65, "n", "n''"),
      localDef(68, 68, "m", "m'"),
      ref(73, 73, "S", "suc", HighlightInfo.DefKind.Con),
      ref(76, 78, "add", HighlightInfo.DefKind.Fn),
      localRef(80, 80, "n", "n''"),
      localRef(82, 82, "m", "m'")
    );
  }

  @Test
  public void unformatTest() {
    @Language("Aya") String code = """
         open
      data  Nat |
      O | S Nat
      """;

    highlightAndTest(code,
      keyword(3, 6, "open"),
      keyword(8, 11, "data"),
      def(14, 16, "Nat", "nat", HighlightInfo.DefKind.Data),
      def(20, 20, "O", HighlightInfo.DefKind.Con),
      def(24, 24, "S", HighlightInfo.DefKind.Con),
      ref(26, 28, "Nat", "nat", HighlightInfo.DefKind.Data));
  }

  @Test
  public void incorrectTest() {
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
}
