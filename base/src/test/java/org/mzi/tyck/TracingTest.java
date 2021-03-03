// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.mzi.tyck;

import org.glavo.kala.tuple.Unit;
import org.junit.jupiter.api.Test;
import org.mzi.test.ThrowingReporter;
import org.mzi.tyck.trace.MdUnicodeTrace;
import org.mzi.tyck.trace.Trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TracingTest {
  @Test
  public void traceExistence() {
    var checker = new ExprTycker(ThrowingReporter.INSTANCE);
    checker.traceBuilder = new Trace.Builder();
    TyckFnTest.idLamTestCase(TyckFnTest.lamConnected(), checker);
    final var tops = checker.traceBuilder.tops;
    assertFalse(tops.isEmpty());
    assertEquals(1, tops.size());
  }

  @Test
  public void traceMd() {
    var checker = new ExprTycker(ThrowingReporter.INSTANCE);
    checker.traceBuilder = new Trace.Builder();
    TyckFnTest.idLamTestCase(TyckFnTest.lamConnected(), checker);
    var show = new MdUnicodeTrace();
    show.lineSep = "\n";
    checker.traceBuilder.root().forEach(e -> e.accept(show, Unit.unit()));
    assertEquals("""
      + \u22A2 `\\lam (_) => \\lam (a) => a` : \\Pi (A : \\oo-Type) -> \\Pi (a : A) -> A
        + \u22A2 `\\lam (a) => a` : \\Pi (a : _) -> _
          + \u22A2 `a` : _
            + \u22A2 _ \u2261 _""", show.builder.toString().trim());
  }
}
