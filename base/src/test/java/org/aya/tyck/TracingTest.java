// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import org.aya.concrete.stmt.TeleDecl;
import org.aya.core.repr.AyaShape;
import org.aya.tyck.trace.MarkdownTrace;
import org.aya.tyck.trace.Trace;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TracingTest {
  @Language("Aya")
  public static final String CODE = """
    open data Nat : Type | zero | suc Nat
    def max (a b : Nat) : Nat
     | zero, b => b
     | a, zero => a
     | suc a, suc b => suc (max a b)""";

  @Test public void traceExistence() {
    var builder = mkBuilder(CODE);
    final var tops = Objects.requireNonNull(builder).getTops();
    assertFalse(tops.isEmpty());
    assertEquals(1, tops.size());
  }

  @NotNull private Trace.Builder mkBuilder(@Language("Aya") String code) {
    var res = TyckDeclTest.successDesugarDecls(code);
    var decls = res.component2();
    var builder = new Trace.Builder();
    var shapes = new AyaShape.Factory();
    decls.forEach(decl -> {
      if (decl instanceof TeleDecl<?> signatured) TyckDeclTest.tyck(res.component1(), signatured, builder, shapes);
    });
    return builder;
  }

  @Test public void traceMd() {
    assertFalse(new MarkdownTrace().docify(Objects.requireNonNull(mkBuilder(CODE))).debugRender().isEmpty());
  }

  @Test public void traceHole() {
    assertFalse(new MarkdownTrace().docify(Objects.requireNonNull(mkBuilder("""
      open data Nat : Type | zero | suc Nat
      def wow {A : Type 1} {B : A -> Type} (a b : A) (x : B a) (y : B b) : Nat => zero
      example def test (A B : Type) (x : A) (y : B) => wow A B x y
      """))).debugRender().isEmpty());
  }
}
