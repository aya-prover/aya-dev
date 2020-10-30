// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core;

import asia.kala.Unit;
import org.junit.jupiter.api.Test;
import org.mzi.core.visitor.UsagesConsumer;
import org.mzi.test.Lisp;
import org.mzi.test.LispTestCase;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UsagesTest extends LispTestCase {
  @Test
  public void someUsages() {
    var term = Lisp.reallyParse("(app glavo glavo)", vars);
    var consumer = new UsagesConsumer(vars.get("glavo"));
    term.accept(consumer, Unit.unit());
    assertEquals(2, consumer.usageCount());
  }

  @Test
  public void noUsages() {
    var term = Lisp.reallyParse("(app xy r)", vars);
    var consumer = new UsagesConsumer(vars.get("a"));
    term.accept(consumer, Unit.unit());
    assertEquals(0, consumer.usageCount());
  }
}
