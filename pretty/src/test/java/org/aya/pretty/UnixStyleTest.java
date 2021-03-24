// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty;

import org.aya.pretty.backend.string.StringPrinterConfig;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.junit.jupiter.api.Test;

public class UnixStyleTest {
  @Test
  public void testUnixStyle() {
    var a = Doc.styled(Style.bold(), "bold");
    var b = Doc.styled(Style.italic(), "italic");
    var c = Doc.styled(Style.bold().and().italic().color("#f08f68"), "color1");
    var d = Doc.styled(Style.bold().and().italic().colorBG("#f08f68"), "color2");
    var e = Doc.styled(Style.strike(), Doc.cat(a, b, c, d));
    System.out.println(e.renderToString(StringPrinterConfig.unixTerminal()));
  }
}
