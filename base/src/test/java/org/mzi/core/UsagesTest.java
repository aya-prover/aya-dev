package org.mzi.core;

import asia.kala.Unit;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mzi.api.ref.Var;
import org.mzi.core.visitor.UsagesConsumer;
import org.mzi.test.Lisp;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UsagesTest {
  @Test
  public void someUsages() {
    @NotNull Map<String, @NotNull Var> refs = new TreeMap<>();
    var term = Lisp.reallyParse("(app glavo glavo)", refs);
    var consumer = new UsagesConsumer(refs.get("glavo"));
    term.accept(consumer, Unit.unit());
    assertEquals(2, consumer.usageCount());
  }

  @Test
  public void noUsages() {
    @NotNull Map<String, @NotNull Var> refs = new TreeMap<>();
    var term = Lisp.reallyParse("(app xy r)", refs);
    var consumer = new UsagesConsumer(refs.get("a"));
    term.accept(consumer, Unit.unit());
    assertEquals(0, consumer.usageCount());
  }
}
