// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli;

import org.aya.cli.literate.HighlightInfoType;
import org.aya.util.reporter.Reporter;
import org.aya.util.reporter.ThrowingReporter;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.aya.cli.HighlighterTester.*;

public class HighlighterTest {
  public final static Reporter reporter = ThrowingReporter.INSTANCE;

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
      keyword(0, 4, "open"),
      whatever(),
      def(10, 13, "Nat", "nat", HighlightInfoType.DefKind.Data),
      def(16, 17, "O", "zero", HighlightInfoType.DefKind.Con),
      def(20, 21, "S", "suc", HighlightInfoType.DefKind.Con),
      ref(22, 25, "Nat", "nat", HighlightInfoType.DefKind.Data),
      whatever(),   // def
      def(31, 34, "add", HighlightInfoType.DefKind.Fn),
      ref(35, 38, "Nat", "nat", HighlightInfoType.DefKind.Data),
      ref(39, 42, "Nat", "nat", HighlightInfoType.DefKind.Data),
      ref(45, 48, "Nat", "nat", HighlightInfoType.DefKind.Data),
      localDef(51, 52, "n", "n'"),
      litInt(54, 55, 0),
      localRef(59, 60, "n", "n'"),
      ref(63, 64, "S", "suc", HighlightInfoType.DefKind.Con),
      // Note: n' is still available here, so use another name
      localDef(65, 66, "n", "n''"),
      localDef(68, 69, "m", "m'"),
      ref(73, 74, "S", "suc", HighlightInfoType.DefKind.Con),
      ref(76, 79, "add", HighlightInfoType.DefKind.Fn),
      localRef(80, 81, "n", "n''"),
      localRef(82, 83, "m", "m'")
    );
  }
}
