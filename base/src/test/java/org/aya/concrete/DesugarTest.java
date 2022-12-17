// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.def.PrimDef;
import org.aya.pretty.AyaPrettierOptions;
import org.aya.pretty.doc.Doc;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.EmptyContext;
import org.aya.test.AyaThrowingReporter;
import org.aya.util.error.Global;
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

  @Test public void doIdiom() {
    desugarAndPretty("def >>= => {??}\ndef test => do { 1 }", "def >>= => {??}\ndef test => 1");
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

  private void desugarAndPretty(@NotNull @NonNls @Language("Aya") String code, @NotNull @NonNls @Language("Aya") String pretty) {
    var resolveInfo = new ResolveInfo(new PrimDef.Factory(), new EmptyContext(AyaThrowingReporter.INSTANCE, Path.of("dummy")).derive("dummy"), ImmutableSeq.empty());
    var stmt = ParseTest.parseStmt(code);
    stmt.forEach(s -> s.desugar(resolveInfo));
    assertEquals(pretty.trim(), Doc.vcat(stmt.view()
        .map(s -> s.toDoc(AyaPrettierOptions.debug())))
      .debugRender()
      .trim());
  }
}
