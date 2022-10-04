// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.desugar.AyaBinOpSet;
import org.aya.core.def.PrimDef;
import org.aya.pretty.doc.Doc;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.EmptyContext;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.Global;
import org.aya.util.reporter.ThrowingReporter;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("UnknownLanguage")
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

  @Test public void arrays() {
    desugarAndPretty("""
    def simpleList => [ 1, 2, 3 ]
    def listGenerator => [ x * y | x <- xs, y <- ys ]
    """, """
    def simpleList => :< 1 (:< 2 (:< 3 nil))
    def listGenerator => >>= xs (\\ (x : _) => >>= ys (\\ (y : _) => pure (x * y)))
    """);   // Aya doesn't know the `:<` and `>>=` are infix function in this case, how to let she know?
  }

  private void desugarAndPretty(@NotNull @NonNls @Language("Aya") String code, @NotNull @NonNls @Language("Aya") String pretty) {
    var resolveInfo = new ResolveInfo(new PrimDef.Factory(), new EmptyContext(ThrowingReporter.INSTANCE, Path.of("dummy")).derive("dummy"), ImmutableSeq.empty(), new AyaBinOpSet(ThrowingReporter.INSTANCE));
    var stmt = ParseTest.parseStmt(code);
    stmt.forEach(s -> s.desugar(resolveInfo));
    assertEquals(pretty.trim(), Doc.vcat(stmt.view()
        .map(s -> s.toDoc(DistillerOptions.debug())))
      .debugRender()
      .trim());
  }
}
