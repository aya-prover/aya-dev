// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PlainReplTest extends ReplTestBase {
  @Test public void exit() {
    assertNotNull(repl(""));
  }

  @Test public void help() {
    var repl = repl(":help")._1;
    assertTrue(repl.contains("help"));
    assertTrue(repl.contains("REPL"));
  }

  @Test public void emptyLine() {
    assertNotNull(repl("\n\n\n"));
  }

  @Test public void redefinition() {
    assertNotNull(repl("def test => Type\ndef test => Type")._1);
  }

  @Test public void illTyped() {
    assertNotNull(repl("prim I\ndef test : I => Type")._2);
  }

  @Test public void load() {
    assertNotNull(repl(":l ../base/src/test/resources/success/add-comm.aya")._1);
  }

  @Test public void typeType() {
    assertTrue(repl(":type Type")._1.contains("Type"));
  }

  @Test public void amb() {
    assertTrue(repl(":p")._2.contains("Ambiguous"));
  }

  @Test public void notFound() {
    assertTrue(repl(":cthulhu")._2.contains("not found"));
  }

  @Test public void type() {
    assertTrue(repl("Type")._1.contains("Type"));
  }

  @Test public void disableUnicode() {
    assertTrue(repl(":unicode no")._1.contains("disable"));
    assertTrue(repl(":unicode yes")._1.contains("enable"));
    assertTrue(repl(":unicode false")._1.contains("disable"));
  }

  @Test public void pwd() {
    // This is actually not very good (maybe people just
    //  clone this repo to some really weird places?) but anyways
    assertTrue(repl(":pwd")._1.contains("aya"));
  }

  @Test public void typeSuc() {
    var repl = repl("data Nat : Type | suc Nat | zero\n:type Nat::suc")._1;
    assertTrue(repl.contains("Nat"));
    assertTrue(repl.contains("->"));
  }
}
