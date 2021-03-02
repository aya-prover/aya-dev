// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.mzi.tyck;

import org.junit.jupiter.api.Test;
import org.mzi.test.ThrowingReporter;
import org.mzi.tyck.trace.Trace;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class TracingTest {
  @Test
  public void traceExistence() {
    var checker = new ExprTycker(ThrowingReporter.INSTANCE);
    checker.traceBuilder = new Trace.Builder();
    TyckFnTest.idLamTestCase(TyckFnTest.lamConnected(), checker);
    assertFalse(checker.traceBuilder.tops.isEmpty());
  }
}
