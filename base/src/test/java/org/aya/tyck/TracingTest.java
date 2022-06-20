// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import org.aya.concrete.stmt.TopTeleDecl;
import org.aya.tyck.trace.MdUnicodeTrace;
import org.aya.tyck.trace.Trace;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TracingTest {
  @Language("TEXT")
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

  @NotNull private Trace.Builder mkBuilder(@Language("TEXT") String code) {
    var res =  TyckDeclTest.successDesugarDecls(code);
    var decls = res._2;
    var builder = new Trace.Builder();
    decls.forEach(decl -> {
      if (decl instanceof TopTeleDecl signatured) TyckDeclTest.tyck(res._1, signatured, builder);
    });
    return builder;
  }

  @Test public void traceMd() {
    assertFalse(new MdUnicodeTrace().docify(Objects.requireNonNull(mkBuilder(CODE))).debugRender().isEmpty());
  }

  @Test public void traceHole() {
    assertFalse(new MdUnicodeTrace().docify(Objects.requireNonNull(mkBuilder("""
      open data Nat : Type | zero | suc Nat
      def wow {A : Type 1} {B : A -> Type} (a b : A) (x : B a) (y : B b) : Nat => zero
      example def test (A B : Type) (x : A) (y : B) => wow A B x y
      """))).debugRender().isEmpty());
  }
}
