// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import org.aya.test.ThrowingReporter;
import org.aya.tyck.trace.MdUnicodeTrace;
import org.aya.tyck.trace.Trace;
import org.glavo.kala.tuple.Unit;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TracingTest {
  @Test
  public void traceExistence() {
    var checker = new ExprTycker(ThrowingReporter.INSTANCE, new Trace.Builder());
    TyckExprTest.idLamTestCase(TyckExprTest.lamConnected(), checker);
    final var tops = Objects.requireNonNull(checker.traceBuilder).getTops();
    assertFalse(tops.isEmpty());
    assertEquals(1, tops.size());
  }

  @Test
  public void traceMd() {
    var checker = new ExprTycker(ThrowingReporter.INSTANCE, new Trace.Builder());
    TyckExprTest.idLamTestCase(TyckExprTest.lamConnected(), checker);
    var show = new MdUnicodeTrace();
    show.lineSep = "\n";
    Objects.requireNonNull(checker.traceBuilder).root().forEach(e -> e.accept(show, Unit.unit()));
    assertEquals("""
        + \u22A2 `\\ (_) => \\ (a) => a` : Pi (A : ooType w) -> Pi (a : A) -> A
          + \u22A2 `\\ (a) => a` : Pi (a : _) -> _
            + \u22A2 `a` : _
              + \u22A2 _ \u2261 _
              + result ⊢ `a` ↑ _
            + result ⊢ `\\ (a : _) => a` ↑ Pi (a : _) -> _
          + result ⊢ `\\ (_ : ooType w) => \\ (a : _) => a` ↑ Pi (A : ooType w) -> Pi (a : A) -> A"""
      , show.builder.toString().trim());
  }
}
