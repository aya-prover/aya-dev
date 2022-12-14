// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty;

import org.junit.jupiter.api.Test;

import static org.aya.pretty.doc.Doc.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author kiva
 */
public class DocStringPrinterTest {
  @Test
  public void testHang() {
    var doc = hang(4, plain("boynextdoor"));
    assertEquals("    boynextdoor", doc.commonRender());
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
