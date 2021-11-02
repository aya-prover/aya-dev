// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli;

import org.aya.cli.repl.jline.AyaReplParser;
import org.aya.cli.repl.jline.JlineRepl;
import org.aya.pretty.doc.Doc;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JlineTest {
  private static AyaReplParser parser;
  private static JlineRepl repl;

  @BeforeAll public static void setup() throws IOException {
    repl = new JlineRepl(PlainReplTest.config);
    parser = new AyaReplParser(repl.commandManager);
  }

  @Test public void sanity() {
    assertEquals("", repl.renderDoc(Doc.empty()));
  }

  @Test public void command() {
    assertEquals(List.of(":type", "Type"), parser.parse(":type Type", 2).words());
  }

  @Test public void sucZero() {
    assertEquals(List.of("suc", "zero"), parser.parse("suc zero", 2).words());
  }

  @Test public void ws() {
    assertEquals("zero", parser.parse("suc     zero", 5).word());
    assertEquals("", parser.parse("suc  zero      ", 12).word());
  }

  @Test public void sucZeroIx() {
    assertEquals(0, parser.parse("suc zero", 0).wordIndex());
    assertEquals(0, parser.parse("suc zero", 1).wordIndex());
    assertEquals(0, parser.parse("suc zero", 2).wordIndex());
    assertEquals(0, parser.parse("suc zero", 3).wordIndex());
    assertEquals(1, parser.parse("suc zero", 4).wordIndex());
    assertEquals(1, parser.parse("suc zero", 5).wordIndex());
    assertEquals(1, parser.parse("suc zero", 6).wordIndex());
    assertEquals(1, parser.parse("suc zero", 7).wordIndex());
    var lastToken = parser.parse("suc zero", 8);
    assertEquals(1, lastToken.wordIndex());
    assertEquals("zero", lastToken.word());
  }
}
