// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core;

import org.aya.core.visitor.UsageCounter;
import org.aya.ref.LocalVar;
import org.aya.test.Lisp;
import org.aya.test.LispTestCase;
import org.glavo.kala.tuple.Unit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UsagesTest extends LispTestCase {
  @Test
  public void someUsages() {
    var term = Lisp.parse("(app glavo glavo)", vars);
    var consumer = new UsageCounter(vars.get("glavo"));
    term.accept(consumer, Unit.unit());
    assertEquals(2, consumer.usageCount());
  }

  @Test
  public void lambdaUsages() {
    var term = Lisp.parse("(lam (dio (U) ex) dio)", vars);
    var consumer = new UsageCounter(vars.get("dio"));
    term.accept(consumer, Unit.unit());
    assertEquals(1, consumer.usageCount());
  }

  @Test
  public void noUsages() {
    var term = Lisp.parse("(app xy r)", vars);
    var consumer = new UsageCounter(new LocalVar("a"));
    term.accept(consumer, Unit.unit());
    assertEquals(0, consumer.usageCount());
  }
}
