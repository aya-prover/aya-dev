// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.latex;

import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.pretty.backend.string.ClosingStylist;
import org.aya.pretty.backend.string.StringPrinter;
import org.aya.pretty.doc.Style;
import org.aya.pretty.doc.Styles;
import org.aya.pretty.printer.ColorScheme;
import org.aya.pretty.printer.StyleFamily;
import org.aya.pretty.style.AyaColorScheme;
import org.aya.pretty.style.AyaStyleFamily;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public class TeXStylist extends ClosingStylist {
  public static final @NotNull TeXStylist DEFAULT = new TeXStylist(AyaColorScheme.INTELLIJ, AyaStyleFamily.DEFAULT, false);
  public static final @NotNull TeXStylist DEFAULT_KATEX = new TeXStylist(AyaColorScheme.INTELLIJ, AyaStyleFamily.DEFAULT, true);

  public final boolean isKaTeX;

  public TeXStylist(@NotNull ColorScheme colorScheme, @NotNull StyleFamily styleFamily, boolean isKaTeX) {
    super(colorScheme, styleFamily);
    this.isKaTeX = isKaTeX;
  }

  @Override protected @NotNull StyleToken formatItalic(EnumSet<StringPrinter.Outer> outer) {
    return new StyleToken("\\textit{", "}", false);
  }

  @Override protected @NotNull StyleToken formatBold(EnumSet<StringPrinter.Outer> outer) {
    return new StyleToken("\\textbf{", "}", false);
  }

  @Override
  protected @NotNull StyleToken formatLineThrough(@NotNull Style.LineThrough line, EnumSet<StringPrinter.Outer> outer) {
    return switch (line.position()) {
      case Underline -> new StyleToken("\\underline{", "}", false);
      case Strike -> new StyleToken("\\sout{", "}", false);
      case Overline -> StyleToken.NULL;
    };
  }

  @Override protected @NotNull StyleToken formatColorHex(int rgb, boolean background) {
    var cmd = "\\%s%s{%06x}{".formatted(
      background ? "colorbox" : "textcolor",
      isKaTeX ? "" : "[HTML]",
      rgb);
    return new StyleToken(cmd, "}", false);
  }

  public static class ClassedPreset extends Delegate {
    public ClassedPreset(@NotNull ClosingStylist delegate) {
      super(delegate);
    }

    @Override
    protected @NotNull ImmutableSeq<StyleToken> formatPresetStyle(@NotNull String styleName, EnumSet<StringPrinter.Outer> outer) {
      var commandName = styleKeyToTex(styleName);
      return ImmutableSeq.of(new StyleToken("\\%s{".formatted(commandName), "}", false));
    }
  }

  /** @see org.aya.pretty.style.AyaStyleKey */
  public static @NotNull String styleKeyToTex(@NotNull String styleName) {
    return normalizeTexId(styleName);
  }

  public static @NotNull String normalizeTexId(@NotNull String id) {
    return id.replace("::", "");
  }

  public static @NotNull String stylesToTexCmd(@NotNull Styles styles, @NotNull String arg) {
    return styles.styles().view()
      .mapNotNull(TeXStylist::styleToTex)
      .foldLeft(arg, (acc, t) -> t.component1() + acc + t.component2());
  }

  public static @Nullable Tuple2<String, String> styleToTex(@NotNull Style style) {
    return switch (style) {
      case Style.Attr attr -> switch (attr) {
        case Italic -> Tuple.of("\\textit{", "}");
        case Bold -> Tuple.of("\\textbf{", "}");
      };
      case Style.LineThrough(var pos, _, _) -> switch (pos) {
        case Strike -> Tuple.of("\\sout{", "}");
        case Underline -> Tuple.of("\\underline{", "}");
        case Overline -> null;
      };
      case Style.ColorHex(var rgb, var background) -> Tuple.of("\\%s[HTML]{%06x}{".formatted(
        background ? "colorbox" : "textcolor", rgb), "}");
      case Style.ColorName(var name, var background) -> Tuple.of("\\%s{%s}{".formatted(
        background ? "colorbox" : "textcolor", normalizeTexId(name)), "}");
      default -> null;
    };
  }

  public static @NotNull String colorsToTex(@NotNull ColorScheme colorScheme) {
    return colorScheme.definedColors().toImmutableSeq().view()
      .map(t -> "\\definecolor{%s}{HTML}{%06x}".formatted(normalizeTexId(t.component1()), t.component2()))
      .joinToString("\n");
  }
}
