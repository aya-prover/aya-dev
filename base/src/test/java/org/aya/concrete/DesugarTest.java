// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete;

import org.aya.api.Global;
import org.aya.api.distill.DistillerOptions;
import org.aya.concrete.desugar.BinOpSet;
import org.aya.pretty.doc.Doc;
import org.aya.test.ThrowingReporter;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DesugarTest {
  @BeforeAll public static void enter() {
    Global.NO_RANDOM_NAME = true;
  }

  @AfterAll public static void exit() {
    Global.reset();
  }

  @Test public void simpleUniv() {
    desugarAndPretty("def test => Type", "def test => Type lp lp");
    desugarAndPretty("def test => Set", "def test => Set lp");
    desugarAndPretty("def test => Prop", "def test => Prop 0");
    desugarAndPretty("def test => ooType", "def test => ooType lp");
  }

  @Test public void modules() {
    desugarAndPretty("""
      module Nat {
       open data ℕ : Set | zero | suc ℕ
      }
      """, """
      module Nat {
        data ℕ : Set lp
          | zero
          | suc (_ : ℕ)
        open ℕ hiding ()
      }""");
  }

  private void desugarAndPretty(@NotNull @NonNls @Language("TEXT") String code, @NotNull @NonNls @Language("TEXT") String pretty) {
    var stmt = ParseTest.parseStmt(code);
    stmt.forEach(s -> s.desugar(ThrowingReporter.INSTANCE, new BinOpSet(ThrowingReporter.INSTANCE)));
    assertEquals(pretty.trim(), Doc.vcat(stmt.view()
        .map(s -> s.toDoc(DistillerOptions.DEBUG)))
      .debugRender()
      .trim());
  }
}
