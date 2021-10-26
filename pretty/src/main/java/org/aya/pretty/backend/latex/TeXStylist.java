// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.latex;

import org.aya.pretty.backend.string.style.ClosingStylist;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;

public class TeXStylist extends ClosingStylist {
  @Override protected @NotNull StyleToken formatItalic() {
    return new StyleToken("\\textit{", "}", false);
  }

  @Override protected @NotNull StyleToken formatBold() {
    return new StyleToken("\\textbf{", "}", false);
  }

  @Override protected @NotNull StyleToken formatStrike() {
    return new StyleToken("\\sout{", "}", false);
  }

  @Override protected @NotNull StyleToken formatUnderline() {
    return new StyleToken("\\underline{", "}", false);
  }

  @Override protected @NotNull StyleToken formatCode() {
    return new StyleToken("\\fbox{", "}", false);
  }

  @Override protected @NotNull StyleToken formatColorHex(int rgb, boolean background) {
    return new StyleToken("\\%s[HTML]{%06x}{".formatted(
      background ? "colorbox" : "textcolor", rgb), "}", false);
  }

  @Override protected @NotNull StyleToken formatCustom(Style.@NotNull CustomStyle style) {
    return new StyleToken("", "", false);
  }
}
