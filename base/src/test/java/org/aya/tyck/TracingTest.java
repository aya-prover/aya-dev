// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import org.aya.concrete.Decl;
import org.aya.test.ThrowingReporter;
import org.aya.tyck.trace.MdUnicodeTrace;
import org.aya.tyck.trace.Trace;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TracingTest {
  @Test public void traceExistence() {
    var builder = mkBuilder();
    final var tops = Objects.requireNonNull(builder).getTops();
    assertFalse(tops.isEmpty());
    assertEquals(1, tops.size());
  }

  @NotNull private Trace.Builder mkBuilder() {
    var decls = TyckDeclTest.successDesugarDecls("""
      open data Nat : Set | zero | suc Nat
      def max (a b : Nat) : Nat
       | zero, b => b
       | a, zero => a
       | suc a, suc b => suc (max a b)""");
    var builder = new Trace.Builder();
    decls.forEach(decl -> {
      if (decl instanceof Decl signatured) signatured.tyck(ThrowingReporter.INSTANCE, builder);
    });
    return builder;
  }

  @Test public void traceMd() {
    var builder = mkBuilder();
    var show = new MdUnicodeTrace();
    assertFalse(show.docify(Objects.requireNonNull(builder)).debugRender().isEmpty());
  }
}
