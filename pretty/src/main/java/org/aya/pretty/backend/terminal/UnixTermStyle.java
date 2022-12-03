// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.terminal;

import org.aya.pretty.doc.Style;

public enum UnixTermStyle implements Style.CustomStyle {
  Dim,
  DoubleUnderline,
  CurlyUnderline,
  Blink,
  Overline,
  Reverse,
  TerminalRed,
  TerminalGreen,
  TerminalBlue,
  TerminalYellow,
  TerminalPurple,
  TerminalCyan,
}
