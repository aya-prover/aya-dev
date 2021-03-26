// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core;

import org.aya.core.def.Def;
import org.aya.core.visitor.RefFinder;
import org.aya.core.visitor.UsageCounter;
import org.aya.ref.LocalVar;
import org.aya.test.Lisp;
import org.aya.test.LispTestCase;
import org.aya.tyck.TyckDeclTest;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.tuple.Unit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UsagesTest extends LispTestCase {
  @Test public void someUsages() {
    var term = Lisp.parse("(app glavo glavo)", vars);
    var consumer = new UsageCounter(vars.get("glavo"));
    term.accept(consumer, Unit.unit());
    assertEquals(2, consumer.usageCount());
  }

  @Test public void lambdaUsages() {
    var term = Lisp.parse("(lam (dio (U) ex) dio)", vars);
    var consumer = new UsageCounter(vars.get("dio"));
    term.accept(consumer, Unit.unit());
    assertEquals(1, consumer.usageCount());
  }

  @Test public void tupUsages() {
    var term = Lisp.parse("(tup (U) yume)", vars);
    var consumer = new UsageCounter(vars.get("yume"));
    term.accept(consumer, Unit.unit());
    assertEquals(1, consumer.usageCount());
  }

  @Test public void piUsages() {
    var term = Lisp.parse("(Pi (giogio (U) ex) giogio)", vars);
    var consumer = new UsageCounter(vars.get("giogio"));
    term.accept(consumer, Unit.unit());
    assertEquals(1, consumer.usageCount());
  }

  @Test public void noUsages() {
    var term = Lisp.parse("(app xy r)", vars);
    var consumer = new UsageCounter(new LocalVar("a"));
    term.accept(consumer, Unit.unit());
    assertEquals(0, consumer.usageCount());
  }

  @Test public void refFinder() {
    assertTrue(RefFinder.HEADER_AND_BODY.withBody());
    TyckDeclTest.successTyckDecls("""
      \\open \\data Nat : \\Set | zero | suc Nat
      \\def one : Nat => suc zero
      \\open \\data Int : \\Set | pos Nat | neg Nat { | zero => pos zero }
      \\def abs (a : Int) : Nat
       | pos n => n
       | neg n => n
      \\open \\data Fin (n : Nat) : \\Set | suc m => fzero | suc m => fsuc (Fin m)
      """).forEach(def -> {
      var of = Buffer.<Def>of();
      def.accept(RefFinder.HEADER_AND_BODY, of);
      assertFalse(of.isEmpty());
      of.clear();
      def.accept(RefFinder.HEADER_ONLY, of);
      assertFalse(of.isEmpty());
    });
  }
}
