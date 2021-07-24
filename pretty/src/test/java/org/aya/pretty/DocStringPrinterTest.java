// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty;

import org.junit.jupiter.api.Test;

import static org.aya.pretty.doc.Doc.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author kiva
 */
public class DocStringPrinterTest {
  @Test
  public void testAlign() {
    var doc = hsep(plain("lorem"), align(vsep(plain("ipsum"), plain("dolor"))));
    assertEquals("lorem ipsum\n      dolor", doc.renderWithPageWidth(80));
  }

  @Test
  public void testHang() {
    var doc = hang(4, plain("boynextdoor"));
    assertEquals("    boynextdoor", doc.renderWithPageWidth(80));
  }

  @Test public void testNestedNest() {
    var doc = vcat(
      plain("shakedown street"),
      nest(2, vcat(
        hcat(plain("grateful "), plain("dead")),
        nest(2, vcat(
          plain("heaven's on fire"),
          plain("kiss"))))));
    assertEquals("""
      shakedown street
        grateful dead
          heaven's on fire
          kiss""", doc.renderWithPageWidth(80));
  }

  @Test
  public void testVCat() {
    var doc = vcat(plain("11"), plain("45"), plain("14"));
    assertEquals("11\n45\n14", doc.renderWithPageWidth(80));
  }

  @Test
  public void testVSep() {
    var doc = vsep(plain("11"), plain("45"), plain("14"));
    assertEquals("11\n45\n14", doc.renderWithPageWidth(80));
  }

  @Test
  public void testHCat() {
    var doc = hcat(plain("11"), plain("45"), plain("14"));
    assertEquals("114514", doc.renderWithPageWidth(80));
  }

  @Test
  public void testHSep() {
    var doc = hsep(plain("11"), plain("45"), plain("14"));
    assertEquals("11 45 14", doc.renderWithPageWidth(80));
  }

  @Test
  public void testHSepNarrow() {
    var doc = hsep(plain("boynext"), plain("next"), plain("door"), plain("doooor"));
    assertEquals("boynext next door doooor", doc.renderWithPageWidth(5));
  }

  @Test
  public void testFillSepNarrow() {
    var doc = fillSep(plain("boynext"), plain("next"), plain("door"), plain("doooor"));
    assertEquals("boynext\nnext door\ndoooor", doc.renderWithPageWidth(7));
  }

  @Test
  public void testFillSep() {
    var doc = fillSep(plain("boynext"), plain("next"), plain("door"), plain("doooor"));
    assertEquals("boynext next door doooor", doc.renderWithPageWidth(114514));
  }

  @Test
  public void testCat() {
    var doc = cat(plain("text"), plain("to"), plain("lay"), plain("out"));
    assertEquals("texttolayout", doc.renderWithPageWidth(80));
  }

  @Test
  public void testGroup() {
    var doc = group(new Cat(new Cat(plain("hello"), line()), plain("world")));
    assertEquals("hello world", doc.renderWithPageWidth(80));
  }

  @Test
  public void testPlainText() {
    var doc = plain("hello world");
    assertEquals("hello world", doc.renderWithPageWidth(80));
  }

  @Test
  public void testMultiline() {
    var doc = plain("hello\nworld\n11451\n4");
    assertEquals("hello\nworld\n11451\n4", doc.renderWithPageWidth(80));
  }

  @Test
  public void testLineBreak() {
    var doc = plain("hello\n");
    assertEquals("hello\n", doc.renderWithPageWidth(80));
  }

  @Test
  public void testMultiLineBreak() {
    var doc = plain("hello\n\n\n\n");
    assertEquals("hello\n\n\n\n", doc.renderWithPageWidth(80));
  }

  @Test
  public void testEmptyText() {
    var doc = plain("");
    assertEquals("", doc.renderWithPageWidth(80));
  }

  @Test
  public void testEmpty() {
    var doc = empty();
    assertEquals("", doc.renderWithPageWidth(80));
  }
}
