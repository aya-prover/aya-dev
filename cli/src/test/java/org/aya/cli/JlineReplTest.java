// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli;

import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.repl.jline.AyaCompleters;
import org.aya.cli.repl.jline.JlineRepl;
import org.aya.pretty.doc.Doc;
import org.aya.repl.antlr.ReplParser;
import org.jline.reader.Candidate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JlineReplTest {
  private static ReplParser parser;
  private static JlineRepl repl;

  @BeforeAll public static void setup() throws IOException {
    repl = new JlineRepl(ImmutableSeq.empty(), PlainReplTest.config);
    parser = new ReplParser(repl.commandManager, repl);
  }

  @Test public void sanity() {
    assertEquals("", repl.renderDoc(Doc.empty()));
  }

  @Test public void command() {
    assertEquals(List.of(":type", "Type"), parser.parse(":type Type", 2).words());
  }

  @Test public void shellLike() {
    // Different lexing strategy depending on the prefix
    assertEquals(List.of(":cd", "../oh/my/./kiva"), parser.parse(":cd ../oh/my/./kiva", 2).words());
    assertEquals(List.of(":type", ".", "/"), parser.parse(":type ./", 2).words());
  }

  @Test public void shellLike2() {
    // Different lexing strategy depending on the prefix
    assertEquals(List.of(":cd", "(Ty"), parser.parse(":cd (Ty", 2).words());
    assertEquals(List.of(":type", "(", "Ty"), parser.parse(":type (Ty", 2).words());
  }

  @Test public void sucZero() {
    assertEquals(List.of("suc", "zero"), parser.parse("suc zero", 2).words());
  }

  @Test public void ws() {
    assertEquals("zero", parser.parse("suc     zero", 5).word());
    assertEquals("", parser.parse("suc  zero      ", 12).word());
  }

  @Test public void parenTyCode() {
    var line = parser.parse("(Ty", 2);
    var candidates = new ArrayList<Candidate>();
    new AyaCompleters.Code(repl).complete(repl.lineReader, line, candidates);
    assertFalse(candidates.isEmpty());
    assertTrue(candidates.stream().anyMatch(c -> "Type".equals(c.value())));
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
