// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty;

import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Link;
import org.aya.pretty.doc.Style;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class HtmlStyleTest {
  @Test public void testHtmlStyle() {
    assertFalse(doc().renderToHtml().isEmpty());
  }

  @Test public void testTexStyle() {
    assertFalse(doc().renderToTeX().isEmpty());
  }

  @NotNull private Doc doc() {
    var a = Doc.styled(Style.bold(), "bold");
    var b = Doc.styled(Style.italic(), "italic");
    var c = Doc.styled(Style.bold().and().italic().color(0xf08f68), "color1");
    var d = Doc.styled(Style.bold().and().italic().colorBG(0xf08f68), "color2");
    var sym = Doc.cat(Doc.symbol("=>"), Doc.symbol("hey"));
    var e = Doc.styled(Style.strike(), Doc.cat(a, b, c, d, sym));
    var f = Doc.cat(e, Doc.hyperLink("Click me", Link.page("https://google.com")));
    return Doc.cat(f, Doc.hyperLink("Show dialog", Link.page("javascript:alert('hello world');")));
  }

  @NotNull private Doc escapeDoc() {
    return Doc.plain("&<>\"\\");
  }

  @NotNull private Doc unescapeDoc() {
    return Doc.escaped("&<>\"\\");
  }

  @Test public void testEscape() {
    var actual = escapeDoc().renderToHtml(false);
    var expected = "&amp;&lt;&gt;&quot;&bsol;";

    assertEquals(expected, actual);
  }

  @Test public void testUnescape() {
    var actual = unescapeDoc().renderToHtml(false);
    var expected = "&<>\"\\";
    assertEquals(expected, actual);
  }

  private @NotNull Doc bulletDoc() {
    return Doc.bullet(Doc.plain("first"), Doc.plain("second"), Doc.plain("third"));
  }

  private @NotNull Doc orderedDoc() {
    return Doc.ordered(Doc.plain("first"), Doc.plain("second"), Doc.plain("third"));
  }

  @Test
  public void testList() {
    var actual0 = bulletDoc().renderToHtml(false);
    @Language("HTML") var expected0 = "<ul><li>first</li><li>second</li><li>third</li></ul>";
    var actual1 = orderedDoc().renderToHtml(false);
    @Language("HTML") var expected1 = "<ol><li>first</li><li>second</li><li>third</li></ol>";

    assertEquals(expected0, actual0);
    assertEquals(expected1, actual1);
  }
}
