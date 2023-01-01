// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PlainReplTest extends ReplTestBase {
  @Test public void exit() {
    assertEquals("", repl("").component1().trim());
  }

  @Test public void help() {
    var repl = repl(":help").component1();
    assertTrue(repl.contains("help"));
    assertTrue(repl.contains("REPL"));
  }

  @Test public void emptyLine() {
    assertNotNull(repl("\n\n\n"));
  }

  @Test public void redefinition() {
    assertNotNull(repl("def test => Type\ndef test => Type").component1());
  }

  @Test public void illTyped() {
    assertNotNull(repl("prim I\ndef test : I => Type").component2());
  }

  @Test public void load() {
    assertNotNull(repl(":l ../base/src/test/resources/success/add-comm.aya").component1());
  }

  @Test public void typeType() {
    var s = repl(":type Type").component1().trim();
    assertTrue(s.startsWith("Type"));
  }

  @Test public void amb() {
    var s = repl(":p").component2();
    assertTrue(s.startsWith("Ambiguous command name"));
  }

  @Test public void notFound() {
    assertTrue(repl(":cthulhu").component2().contains("not found"));
  }

  @Test public void type() {
    var s = repl("Type").component1().trim();
    assertTrue(s.startsWith("Type"));
  }

  @Test public void disableUnicode() {
    assertTrue(repl(":unicode no").component1().contains("disable"));
    assertTrue(repl(":unicode yes").component1().contains("enable"));
    assertTrue(repl(":unicode false").component1().contains("disable"));
  }

  @Test public void pwd() {
    // This is actually not very good (maybe people just
    //  clone this repo to some really weird places?) but anyways
    assertTrue(repl(":pwd").component1().contains("aya"));
  }

  @Test public void typeSuc() {
    var repl = repl("data Nat : Type | suc Nat | zero\n:type Nat::suc").component1().trim();
    assertEquals("Nat -> Nat", repl);
  }

  @Test public void color() {
    assertTrue(repl(":color i").component1().contains("IntelliJ"));
    assertTrue(repl(":color e").component1().contains("Emacs"));
    assertTrue(repl(":color").component1().contains("Emacs"));
    assertTrue(repl(":color Custom").component2().contains("give"));
    assertTrue(repl(":color " + VscColorThemeTest.TEST_DATA).component1().contains(VscColorThemeTest.TEST_DATA.getFileName().toString()));
  }

  @Test public void style() {
    assertTrue(repl(":style d").component1().contains("Default"));
    assertTrue(repl(":style").component1().contains("Default"));
    assertTrue(repl(":style " + VscColorThemeTest.TEST_DATA).component2().contains("support"));
  }
}
