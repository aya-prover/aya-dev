// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty;

import org.aya.pretty.backend.string.StringLink;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

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
    var e = Doc.styled(Style.strike(), Doc.cat(a, b, c, d));
    var f = Doc.cat(e, Doc.hyperLink("Click me", new StringLink("https://google.com")));
    return Doc.cat(f, Doc.hyperLink("Show dialog", new StringLink("javascript:alert('hello world');")));
  }
}
