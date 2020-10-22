package org.mzi.pretty;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mzi.pretty.backend.DocStringPrinter;
import org.mzi.pretty.doc.Doc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mzi.pretty.doc.Doc.*;

/**
 * @author kiva
 */
public class DocStringPrinterTest {
  @Test
  public void testVCat() {
    var doc = vcat(plain("11"), plain("45"), plain("14"));
    assertEquals("11\n45\n14", withPageWidth(80, doc));
  }

  @Test
  public void testVSep() {
    var doc = vsep(plain("11"), plain("45"), plain("14"));
    assertEquals("11\n45\n14", withPageWidth(80, doc));
  }

  @Test
  public void testHCat() {
    var doc = hcat(plain("11"), plain("45"), plain("14"));
    assertEquals("114514", withPageWidth(80, doc));
  }

  @Test
  public void testHSep() {
    var doc = hsep(plain("11"), plain("45"), plain("14"));
    assertEquals("11 45 14", withPageWidth(80, doc));
  }

  @Test
  public void testHSepNarrow() {
    var doc = hsep(plain("boynext"), plain("next"), plain("door"), plain("doooor"));
    assertEquals("boynext next door doooor", withPageWidth(5, doc));
  }

  @Test
  public void testFillSepNarrow() {
    var doc = fillSep(plain("boynext"), plain("next"), plain("door"), plain("doooor"));
    assertEquals("boynext\nnext\ndoor\ndoooor", withPageWidth(7, doc));
  }

  @Test
  public void testSep() {
    var doc = sep(plain("text"), plain("to"), plain("lay"), plain("out"));
    assertEquals("text to lay out", withPageWidth(80, doc));
  }

  @Test
  public void testCat() {
    var doc = cat(plain("text"), plain("to"), plain("lay"), plain("out"));
    assertEquals("texttolayout", withPageWidth(80, doc));
  }

  @Test
  public void testGroup() {
    var doc = group(new Cat(new Cat(plain("hello"), line()), plain("world")));
    assertEquals("hello world", withPageWidth(80, doc));
  }

  @Test
  public void testSepNarrow() {
    var doc = hsep(plain("prefix"), sep(plain("text"), plain("to"), plain("lay"), plain("out")));
    assertEquals("prefix text\nto\nlay\nout", withPageWidth(20, doc));
  }

  @Test
  public void testPlainText() {
    var doc = plain("hello world");
    assertEquals("hello world", withPageWidth(80, doc));
  }

  @Test
  public void testMultiline() {
    var doc = plain("hello\nworld\n11451\n4");
    assertEquals("hello\nworld\n11451\n4", withPageWidth(80, doc));
  }

  @Test
  public void testLineBreak() {
    var doc = plain("hello\n");
    assertEquals("hello\n", withPageWidth(80, doc));
  }

  @Test
  public void testMultiLineBreak() {
    var doc = plain("hello\n\n\n\n");
    assertEquals("hello\n\n\n\n", withPageWidth(80, doc));
  }

  @Test
  public void testEmptyText() {
    var doc = plain("");
    assertEquals("", withPageWidth(80, doc));
  }

  @Test
  public void testEmpty() {
    var doc = empty();
    assertEquals("", withPageWidth(80, doc));
  }

  private String withPageWidth(int pageWidth, @NotNull Doc doc) {
    var config = new DocStringPrinter.Config(pageWidth);
    return doc.renderToString(config);
  }
}
