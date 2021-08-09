// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import org.aya.concrete.stmt.Decl;
import org.aya.core.def.PrimDef;
import org.aya.test.ThrowingReporter;
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
    open data Nat : Set | zero | suc Nat
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
    var primFactory = PrimDef.PrimFactory.create();
    var decls = TyckDeclTest.successDesugarDecls(code, primFactory);
    var builder = new Trace.Builder();
    decls.forEach(decl -> {
      if (decl instanceof Decl signatured) signatured.tyck(ThrowingReporter.INSTANCE, builder, primFactory);
    });
    return builder;
  }

  @Test public void traceMd() {
    assertFalse(new MdUnicodeTrace().docify(Objects.requireNonNull(mkBuilder(CODE))).debugRender().isEmpty());
  }

  @Test public void traceHole() {
    assertFalse(new MdUnicodeTrace().docify(Objects.requireNonNull(mkBuilder("""
      open data Nat : Set | zero | suc Nat
      def wow {A : Type} {B : A -> Type} (a b : A) (x : B a) (y : B b) : Nat => zero
      example def test (A B : Type) (x : A) (y : B) => wow A B x y
      """))).debugRender().isEmpty());
  }
}
