// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.desugar.AyaBinOpSet;
import org.aya.pretty.doc.Doc;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.EmptyContext;
import org.aya.test.ThrowingReporter;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.Global;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DesugarTest {
  @BeforeAll public static void enter() {
    Global.NO_RANDOM_NAME = true;
  }

  @AfterAll public static void exit() {
    Global.reset();
  }

  @Test public void simpleUniv() {
    desugarAndPretty("def test => Type", "def test => Type 0");
  }

  @Test public void modules() {
    desugarAndPretty("""
      module Nat {
       open data ℕ : Type | zero | suc ℕ
      }
      """, """
      module Nat {
        data ℕ : Type 0
          | zero
          | suc (_ : ℕ)
        open ℕ hiding ()
      }""");
  }

  private void desugarAndPretty(@NotNull @NonNls @Language("TEXT") String code, @NotNull @NonNls @Language("TEXT") String pretty) {
    var resolveInfo = new ResolveInfo(new EmptyContext(ThrowingReporter.INSTANCE, Path.of("dummy")).derive("dummy"), ImmutableSeq.empty(), new AyaBinOpSet(ThrowingReporter.INSTANCE));
    var stmt = ParseTest.parseStmt(code);
    stmt.forEach(s -> s.desugar(resolveInfo));
    assertEquals(pretty.trim(), Doc.vcat(stmt.view()
        .map(s -> s.toDoc(DistillerOptions.debug())))
      .debugRender()
      .trim());
  }
}
