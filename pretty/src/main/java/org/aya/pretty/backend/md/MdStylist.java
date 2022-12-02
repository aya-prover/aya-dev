// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.md;

import org.aya.pretty.backend.html.Html5Stylist;
import org.aya.pretty.doc.Style;
import org.aya.pretty.printer.ColorScheme;
import org.aya.pretty.printer.StyleFamily;
import org.aya.pretty.style.AyaColorScheme;
import org.aya.pretty.style.AyaStyleFamily;
import org.jetbrains.annotations.NotNull;

public class MdStylist extends Html5Stylist {
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

  @Override protected @NotNull StyleToken formatInlineCode(@NotNull String language) {
    return new StyleToken("`", "`", false);
  }

  @Override protected @NotNull StyleToken formatCustom(Style.@NotNull CustomStyle style) {
    if (style instanceof MdStyle md) return switch (md) {
      case MdStyle.GFM gfm -> switch (gfm) {
        case BlockQuote -> new StyleToken(c -> c.invisibleContent("> "), c -> c.lineBreakWith("\n\n"));
        case Paragraph -> new StyleToken(c -> {}, c -> c.lineBreakWith("\n\n"));
      };
      case MdStyle.Heading(int level) -> formatH(level);
      case MdStyle.CodeBlock(var lang) -> new StyleToken(c -> {
        c.lineBreakWith("\n");
        c.invisibleContent("```" + lang);
        c.lineBreakWith("\n");
      }, c -> {
        c.lineBreakWith("\n");
        c.invisibleContent("```");
        c.lineBreakWith("\n\n");
      });
    };
    return super.formatCustom(style);
  }

  private @NotNull StyleToken formatH(int h) {
    return new StyleToken(c -> {
      c.invisibleContent("#".repeat(h));
      c.invisibleContent(" ");
    }, c -> c.lineBreakWith("\n"));
  }
}
