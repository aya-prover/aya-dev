// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.html;

import org.aya.pretty.backend.string.style.ClosingStylist;
import org.aya.pretty.doc.Style;
import org.aya.pretty.printer.ColorScheme;
import org.aya.pretty.printer.StyleFamily;
import org.aya.pretty.style.AyaColorScheme;
import org.aya.pretty.style.AyaStyleFamily;
import org.jetbrains.annotations.NotNull;

public class Html5Stylist extends ClosingStylist {
  public static final @NotNull Html5Stylist DEFAULT = new Html5Stylist(AyaColorScheme.EMACS, AyaStyleFamily.DEFAULT);

  public Html5Stylist(@NotNull ColorScheme colorScheme, @NotNull StyleFamily styleFamily) {
    super(colorScheme, styleFamily);
  }

  @Override protected @NotNull StyleToken formatItalic() {
    return new StyleToken("<i>", "</i>", false);
  }

  @Override protected @NotNull StyleToken formatBold() {
    return new StyleToken("<b>", "</b>", false);
  }

  @Override protected @NotNull StyleToken formatStrike() {
    return new StyleToken("<s>", "</s>", false);
  }

  @Override protected @NotNull StyleToken formatCode() {
    return new StyleToken("<code>", "</code>", false);
  }

  @Override protected @NotNull StyleToken formatUnderline() {
    return new StyleToken("<u>", "</u>", false);
  }

  @Override protected @NotNull StyleToken formatColorHex(int rgb, boolean background) {
    return new StyleToken(
      "<span style=\"%s:#%06x;\">".formatted(background ? "background-color" : "color", rgb),
      "</span>",
      false
    );
  }

  @Override protected @NotNull StyleToken formatCustom(Style.@NotNull CustomStyle style) {
    // TODO: html custom style?
    return StyleToken.NULL;
  }
}
