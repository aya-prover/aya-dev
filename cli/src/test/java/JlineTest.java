// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

import org.aya.cli.repl.jline.AyaReplParser;
import org.aya.cli.repl.jline.JlineRepl;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JlineTest {
  public static @NotNull AyaReplParser PARSER = new AyaReplParser();

  @Test public void initializeJline() throws IOException {
    new JlineRepl(PlainReplTest.config).renderDoc(Doc.empty());
  }

  @Test public void command() {
    assertEquals(List.of(":type", "Type"), PARSER.parse(":type Type", 2).words());
  }

  @Test public void sucZero() {
    assertEquals(List.of("suc", "zero"), PARSER.parse("suc zero", 2).words());
  }

  @Test public void ws() {
    assertEquals("zero", PARSER.parse("suc     zero", 5).word());
    assertEquals("", PARSER.parse("suc  zero      ", 12).word());
  }

  @Test public void sucZeroIx() {
    assertEquals(0, PARSER.parse("suc zero", 0).wordIndex());
    assertEquals(0, PARSER.parse("suc zero", 1).wordIndex());
    assertEquals(0, PARSER.parse("suc zero", 2).wordIndex());
    assertEquals(0, PARSER.parse("suc zero", 3).wordIndex());
    assertEquals(1, PARSER.parse("suc zero", 4).wordIndex());
    assertEquals(1, PARSER.parse("suc zero", 5).wordIndex());
    assertEquals(1, PARSER.parse("suc zero", 6).wordIndex());
    assertEquals(1, PARSER.parse("suc zero", 7).wordIndex());
    var lastToken = PARSER.parse("suc zero", 8);
    assertEquals(1, lastToken.wordIndex());
    assertEquals("zero", lastToken.word());
  }
}
