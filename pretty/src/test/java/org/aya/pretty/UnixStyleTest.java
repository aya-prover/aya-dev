// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty;

import org.aya.pretty.backend.terminal.UnixTermStyle;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class UnixStyleTest {
  @Test
  public void testUnixStyle() {
    var a = Doc.styled(Style.bold(), "bold");
    var b = Doc.styled(Style.italic(), "italic");
    var c = Doc.styled(Style.bold().and().italic().color(0xf08f68), "color1");
    var d = Doc.styled(Style.bold().and().italic().colorBG(0xf08f68), "color2");
    var e = Doc.styled(Style.strike(), Doc.cat(a, b, c, d));
    var f = Doc.vcat(
      e,
      Doc.styled(UnixTermStyle.DoubleUnderline, "double underline"),
      Doc.styled(UnixTermStyle.CurlyUnderline, "curly underline"),
      Doc.styled(UnixTermStyle.Blink, "blink"),
      Doc.styled(UnixTermStyle.Reverse, "reverse"),
      Doc.styled(UnixTermStyle.TerminalRed, "red"),
      Doc.styled(UnixTermStyle.TerminalGreen, "green"),
      Doc.styled(UnixTermStyle.TerminalBlue, "blue"),
      Doc.styled(UnixTermStyle.TerminalYellow, "yellow"),
      Doc.styled(UnixTermStyle.TerminalPurple, "purple"),
      Doc.styled(UnixTermStyle.TerminalCyan, "cyan")
    );
    assertFalse(f.renderToTerminal().isEmpty());
  }
}
