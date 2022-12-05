// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.md;

import org.aya.pretty.backend.html.Html5Stylist;
import org.aya.pretty.backend.string.StringPrinter;
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

  @Override protected @NotNull StyleToken formatItalic(StringPrinter.Outer outer) {
    // Actually, we are abusing the undefined behavior (outer != Free) of markdown.
    // Typical markdown does not allow italic/bold/strike/... in code segments,
    // which means the `outer` should always be `Free`, but Literate Aya uses
    // HTML tag `<pre>` and `<code>` for rendered Aya code, which allows more
    // HTML typesetting to be applied.
    // A solution may be a separate `AyaMdStylist` for Literate Aya only, and
    // the standard markdown backend should always use markdown typesetting
    // and assume `outer == Free` (but don't assert it).
    return outer != StringPrinter.Outer.Free ? super.formatItalic(outer) : new StyleToken("_", "_", false);
  }

  @Override protected @NotNull StyleToken formatBold(StringPrinter.Outer outer) {
    // see comments in `formatItalic`
    return outer != StringPrinter.Outer.Free ? super.formatBold(outer) : new StyleToken("**", "**", false);
  }

  @Override protected @NotNull StyleToken formatStrike(StringPrinter.Outer outer) {
    // see comments in `formatItalic`
    return outer != StringPrinter.Outer.Free ? super.formatStrike(outer) : new StyleToken("~~", "~~", false);
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
    };
    return super.formatCustom(style);
  }
}
