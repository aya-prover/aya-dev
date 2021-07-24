// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.backend.html;

import org.aya.pretty.backend.string.style.ClosingStylist;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;

public class Html5Stylist extends ClosingStylist {
  @Override protected StyleToken formatItalic() {
    return new StyleToken("<i>", "</i>", false);
  }

  @Override protected StyleToken formatBold() {
    return new StyleToken("<b>", "</b>", false);
  }

  @Override protected StyleToken formatStrike() {
    return new StyleToken("<s>", "</s>", false);
  }

  @Override protected StyleToken formatCode() {
    return new StyleToken("<code>", "</code>", false);
  }

  @Override protected StyleToken formatUnderline() {
    return new StyleToken("<u>", "</u>", false);
  }

  @Override protected StyleToken formatColorHex(int rgb, boolean background) {
    return new StyleToken(
      "<span style=\"%s:#%06x;\">".formatted(background ? "background-color" : "color", rgb),
      "</span>",
      false
    );
  }

  @Override protected @NotNull StyleToken formatCustom(Style.@NotNull CustomStyle style) {
    // TODO: html custom style?
    return new StyleToken("", "", false);
  }
}
