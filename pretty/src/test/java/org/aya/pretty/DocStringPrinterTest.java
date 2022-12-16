// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty;

import kala.collection.Seq;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Link;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.aya.pretty.doc.Doc.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author kiva
 */
public class DocStringPrinterTest {
  private static @NotNull Doc aya() {
    return Doc.english("A univalent proof assistant designed for formalizing math and type-directed programming.");
  }

  @Test public void testNestMultilinePrefixed() {
    var doc = Doc.sep(Doc.plain("prefix"), Doc.nest(4, aya()));
    assertEquals("""
        prefix A univalent\s
            proof assistant\s
            designed for\s
            formalizing math
            and\s
            type-directed\s
            programming.""",
      doc.renderToString(20, false));
  }

  @Test public void testHangMultilinePrefixed() {
    var doc = Doc.sep(Doc.plain("prefix"), Doc.hang(4, aya()));
    assertEquals("""
        prefix A univalent\s
                   proof\s
                   assistant
                   designed\s
                   for\s
                   formalizing
                   math and\s
                   type-directed
                   programming.""",
      doc.renderToString(20, false));
  }

  @Test public void testIndentMultilinePrefixed() {
    var doc = Doc.sep(Doc.plain("prefix"), Doc.indent(4, aya()));
    assertEquals("""
        prefix     A\s
                   univalent
                   proof\s
                   assistant
                   designed\s
                   for\s
                   formalizing
                   math and\s
                   type-directed
                   programming.""",
      doc.renderToString(20, false));
  }

  @Test public void testCatBlockR() {
    var list = Doc.catBlockR(
      12,
      Seq.of(
        Doc.commaList(Seq.of(Doc.plain("exit"), Doc.plain("quit"))),
        Doc.english("print-toggle"),
        Doc.plain("help"),
        Doc.hyperLink("cd", Link.page("aa"))
      ),
      Doc.plain(": "),
      Seq.of(
        Doc.english("Quit the REPL"),
        Doc.english("Toggle a pretty printing option"),
        Doc.english("Describe a selected command or show all commands"),
        Doc.english("Change current working directory")
      )
    );
    var doc = Doc.sep(Doc.plain("prefix"), Doc.indent(4, list));
    assertEquals("""
      prefix     exit, quit  : Quit the REPL
                 print-toggle: Toggle a pretty printing\s
                               option
                 help        : Describe a selected\s
                               command or show all\s
                               commands
                 cd          : Change current working\s
                               directory""", doc.renderToString(50, false));
  }

  @Test public void testCatBlockL() {
    var list = Doc.catBlockL(
      18,
      Seq.of(
        Doc.commaList(Seq.of(Doc.plain("exit"), Doc.plain("quit"))),
        Doc.english("print-toggle"),
        Doc.plain("help"),
        Doc.hyperLink("cd", Link.page("aa"))
      ),
      Doc.plain(": "),
      Seq.of(
        Doc.english("Quit the REPL"),
        Doc.english("Toggle a pretty printing option"),
        Doc.english("Describe a selected command or show all commands"),
        Doc.english("Change current working directory")
      )
    );
    var doc = Doc.sep(Doc.plain("prefix"), Doc.indent(4, list));
    assertEquals("""
      prefix     exit, quit:       Quit the REPL
                 print-toggle:     Toggle a pretty\s
                                   printing option
                 help:             Describe a selected\s
                                   command or show all\s
                                   commands
                 cd:               Change current\s
                                   working directory""", doc.renderToString(50, false));
  }

  @Test public void testSplit() {
    var doc = splitR(8, plain("Help"), plain(":"), english("Show the help message"));
    assertEquals("Help    :Show the help message", doc.commonRender());
  }

  @Test public void testSplitR() {
    var doc = splitL(8, plain("Help"), plain(":"), english("Show the help message"));
    assertEquals("Help:   Show the help message", doc.commonRender());
  }

  @Test public void testHang() {
    var doc = hang(4, plain("boynextdoor"));
    assertEquals("    boynextdoor", doc.commonRender());
  }

  @Test public void testIndent() {
    var doc = indent(2, vcat(plain("boynextdoor"), plain("boynextdoor")));
    assertEquals("    boynextdoor\n  boynextdoor", doc.commonRender());
  }

  @Test public void testNestedNest() {
    var doc = vcat(
      plain("shakedown street"),
      nest(2, vcat(
        cat(plain("grateful "), plain("dead")),
        nest(2, vcat(
          plain("heaven's on fire"),
          plain("kiss"))))));
    assertEquals("""
      shakedown street
        grateful dead
          heaven's on fire
          kiss""", doc.commonRender());
  }

  @Test
  public void testVCat() {
    var doc = vcat(plain("11"), plain("45"), plain("14"));
    assertEquals("11\n45\n14", doc.commonRender());
  }

  @Test
  public void testHCat() {
    var doc = cat(plain("11"), plain("45"), plain("14"));
    assertEquals("114514", doc.commonRender());
  }

  @Test
  public void testHSep() {
    var doc = stickySep(plain("11"), plain("45"), plain("14"));
    assertEquals("11 45 14", doc.commonRender());
  }

  @Test
  public void testHSepNarrow() {
    var doc = stickySep(plain("boynext"), plain("next"), plain("door"), plain("doooor"));
    assertEquals("boynext next door doooor", doc.renderToString(5, false));
  }

  @Test
  public void testFillSepNarrow() {
    var doc = sep(plain("boynext"), plain("next"), plain("door"), plain("doooor"));
    assertEquals("boynext\nnext door\ndoooor", doc.renderToString(7, false));
  }

  @Test
  public void testFillSep() {
    var doc = sep(plain("boynext"), plain("next"), plain("door"), plain("doooor"));
    assertEquals("boynext next door doooor", doc.debugRender());
  }

  @Test
  public void testCat() {
    var doc = cat(plain("text"), plain("to"), plain("lay"), plain("out"));
    assertEquals("texttolayout", doc.commonRender());
  }

  @Test
  public void testPlainText() {
    var doc = plain("hello world");
    assertEquals("hello world", doc.commonRender());
  }

  @Test
  public void testMultiline() {
    var doc = plain("hello\nworld\n11451\n4");
    assertEquals("hello\nworld\n11451\n4", doc.commonRender());
  }

  @Test
  public void testLineBreak() {
    var doc = plain("hello\n");
    assertEquals("hello\n", doc.commonRender());
  }

  @Test
  public void testMultiLineBreak() {
    var doc = plain("hello\n\n\n\n");
    assertEquals("hello\n\n\n\n", doc.commonRender());
  }

  @Test
  public void testEmptyText() {
    var doc = plain("");
    assertEquals("", doc.commonRender());
  }

  @Test
  public void testEmpty() {
    var doc = empty();
    assertEquals("", doc.commonRender());
  }

  @Test
  public void testList() {
    var list = cat(
      plain("what"),
      bullet(
        cat(plain("fir"), line(), plain("st")),
        cat(plain("second"), bullet(plain("second.1"))), cat(plain("third"), line()))
    );

    assertEquals("""
      what
      - fir
        st
      - second
        - second.1
      - third
            
      """, list.commonRender());
  }
}
