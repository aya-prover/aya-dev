package org.mzi.pretty;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mzi.pretty.doc.Doc.*;

/**
 * @author kiva
 */
public class DocStringPrinterTest {
  @Test
  public void testHang() {
    var doc = hang(4, plain("boynextdoor"));
    assertEquals("    boynextdoor", doc.withPageWidth(80));
  }

  @Test
  public void testHangMultiline() {
    var doc = hang(4, plain("boynextdoor\ndooooor"));
    assertEquals("    boynextdoor\n    dooooor", doc.withPageWidth(80));
  }

  @Test
  public void testIndent() {
    var doc = indent(4, plain("boynextdoor"));
    assertEquals("        boynextdoor", doc.withPageWidth(80));
  }

  @Test
  public void testIndentMultiline() {
    var doc = indent(4, plain("boynextdoor\ndooooor"));
    assertEquals("        boynextdoor\n    dooooor", doc.withPageWidth(80));
  }

  @Test
  public void testIndentWithPrefix() {
    var doc = hcat(plain("prefix"), indent(4, plain("boynextdoor\ndooooor")));
    assertEquals("prefix    boynextdoor\n          dooooor", doc.withPageWidth(80));
  }

  @Test
  public void testVCat() {
    var doc = vcat(plain("11"), plain("45"), plain("14"));
    assertEquals("11\n45\n14", doc.withPageWidth(80));
  }

  @Test
  public void testVSep() {
    var doc = vsep(plain("11"), plain("45"), plain("14"));
    assertEquals("11\n45\n14", doc.withPageWidth(80));
  }

  @Test
  public void testHCat() {
    var doc = hcat(plain("11"), plain("45"), plain("14"));
    assertEquals("114514", doc.withPageWidth(80));
  }

  @Test
  public void testHSep() {
    var doc = hsep(plain("11"), plain("45"), plain("14"));
    assertEquals("11 45 14", doc.withPageWidth(80));
  }

  @Test
  public void testHSepNarrow() {
    var doc = hsep(plain("boynext"), plain("next"), plain("door"), plain("doooor"));
    assertEquals("boynext next door doooor", doc.withPageWidth(5));
  }

  @Test
  public void testFillSepNarrow() {
    var doc = fillSep(plain("boynext"), plain("next"), plain("door"), plain("doooor"));
    assertEquals("boynext\nnext\ndoor\ndoooor", doc.withPageWidth(7));
  }

  @Test
  public void testSep() {
    var doc = sep(plain("text"), plain("to"), plain("lay"), plain("out"));
    assertEquals("text to lay out", doc.withPageWidth(80));
  }

  @Test
  public void testCat() {
    var doc = cat(plain("text"), plain("to"), plain("lay"), plain("out"));
    assertEquals("texttolayout", doc.withPageWidth(80));
  }

  @Test
  public void testGroup() {
    var doc = group(new Cat(new Cat(plain("hello"), line()), plain("world")));
    assertEquals("hello world", doc.withPageWidth(80));
  }

  @Test
  public void testSepNarrow() {
    var doc = hsep(plain("prefix"), sep(plain("text"), plain("to"), plain("lay"), plain("out")));
    assertEquals("prefix text\nto\nlay\nout", doc.withPageWidth(20));
  }

  @Test
  public void testPlainText() {
    var doc = plain("hello world");
    assertEquals("hello world", doc.withPageWidth(80));
  }

  @Test
  public void testMultiline() {
    var doc = plain("hello\nworld\n11451\n4");
    assertEquals("hello\nworld\n11451\n4", doc.withPageWidth(80));
  }

  @Test
  public void testLineBreak() {
    var doc = plain("hello\n");
    assertEquals("hello\n", doc.withPageWidth(80));
  }

  @Test
  public void testMultiLineBreak() {
    var doc = plain("hello\n\n\n\n");
    assertEquals("hello\n\n\n\n", doc.withPageWidth(80));
  }

  @Test
  public void testEmptyText() {
    var doc = plain("");
    assertEquals("", doc.withPageWidth(80));
  }

  @Test
  public void testEmpty() {
    var doc = empty();
    assertEquals("", doc.withPageWidth(80));
  }
}
