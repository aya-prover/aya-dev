// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.latex;

import org.aya.pretty.backend.string.ClosingStylist;
import org.aya.pretty.printer.ColorScheme;
import org.aya.pretty.printer.StyleFamily;
import org.aya.pretty.style.AyaColorScheme;
import org.aya.pretty.style.AyaStyleFamily;
import org.jetbrains.annotations.NotNull;

public class TeXStylist extends ClosingStylist {
  public static final @NotNull TeXStylist DEFAULT = new TeXStylist(AyaColorScheme.INTELLIJ, AyaStyleFamily.DEFAULT);

  public TeXStylist(@NotNull ColorScheme colorScheme, @NotNull StyleFamily styleFamily) {
    super(colorScheme, styleFamily);
  }

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

  @Override protected @NotNull StyleToken formatColorHex(int rgb, boolean background) {
    return new StyleToken("\\%s[HTML]{%06x}{".formatted(
      background ? "colorbox" : "textcolor", rgb), "}", false);
  }
}
