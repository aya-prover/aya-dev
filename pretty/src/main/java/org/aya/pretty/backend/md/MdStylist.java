// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.md;

import org.aya.pretty.printer.ColorScheme;
import org.aya.pretty.printer.StyleFamily;
import org.aya.pretty.style.AyaColorScheme;
import org.aya.pretty.style.AyaStyleFamily;
import org.jetbrains.annotations.NotNull;

public class MdStylist extends AyaMdStylist {
  public static final @NotNull MdStylist DEFAULT = new MdStylist(AyaColorScheme.EMACS, AyaStyleFamily.DEFAULT);

  public MdStylist(@NotNull ColorScheme colorScheme, @NotNull StyleFamily styleFamily) {
    super(colorScheme, styleFamily);
  }

  @Override protected @NotNull StyleToken formatItalic() {
    return new StyleToken("_", "_", false);
  }

  @Override protected @NotNull StyleToken formatBold() {
    return new StyleToken("**", "**", false);
  }

  @Override protected @NotNull StyleToken formatStrike() {
    return new StyleToken("~~", "~~", false);
  }
}
