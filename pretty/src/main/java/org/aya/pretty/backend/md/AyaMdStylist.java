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

public class AyaMdStylist extends Html5Stylist {
  public static final @NotNull AyaMdStylist DEFAULT = new AyaMdStylist(AyaColorScheme.EMACS, AyaStyleFamily.DEFAULT);

  public AyaMdStylist(@NotNull ColorScheme colorScheme, @NotNull StyleFamily styleFamily) {
    super(colorScheme, styleFamily);
  }

  @Override protected @NotNull StyleToken formatCustom(Style.@NotNull CustomStyle style) {
    if (style instanceof MdStyle md) return switch (md) {
      case MdStyle.GFM gfm -> switch (gfm) {
        case BlockQuote -> new StyleToken(c -> c.invisibleContent("> "), c -> c.lineBreakWith("\n\n"));
        case Paragraph -> new StyleToken(c -> {}, c -> c.lineBreakWith("\n\n"));
      };
      case MdStyle.Heading(int level) -> new StyleToken(c -> {
        c.invisibleContent("#".repeat(level));
        c.invisibleContent(" ");
      }, c -> c.lineBreakWith("\n"));
      case MdStyle.CodeBlock(var lang) -> "aya".equalsIgnoreCase(lang)
        ? formatAyaCodeBlock()
        : formatCodeBlock(lang);
    };
    return super.formatCustom(style);
  }

  private @NotNull StyleToken formatCodeBlock(@NotNull String begin, @NotNull String end) {
    return new StyleToken(c -> {
      c.invisibleContent(begin);
      c.lineBreakWith("\n");
    }, c -> {
      c.lineBreakWith("\n");
      c.invisibleContent(end);
      c.lineBreakWith("\n");
    });
  }

  protected @NotNull StyleToken formatAyaCodeBlock() {
    return formatCodeBlock("<pre class=\"Aya\">", "</pre>");
  }

  protected @NotNull StyleToken formatCodeBlock(@NotNull String lang) {
    return formatCodeBlock("```" + lang, "```");
  }
}
