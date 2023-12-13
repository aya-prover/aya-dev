// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.html;

import kala.collection.immutable.ImmutableSeq;
import org.aya.pretty.backend.string.ClosingStylist;
import org.aya.pretty.backend.string.StringPrinter;
import org.aya.pretty.doc.Style;
import org.aya.pretty.printer.ColorScheme;
import org.aya.pretty.printer.StyleFamily;
import org.aya.pretty.style.AyaColorScheme;
import org.aya.pretty.style.AyaStyleFamily;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public class Html5Stylist extends ClosingStylist {
  public static final @NotNull Html5Stylist DEFAULT = new Html5Stylist(AyaColorScheme.EMACS, AyaStyleFamily.DEFAULT);

  public Html5Stylist(@NotNull ColorScheme colorScheme, @NotNull StyleFamily styleFamily) {
    super(colorScheme, styleFamily);
  }

  @Override protected @NotNull StyleToken formatItalic(EnumSet<StringPrinter.Outer> outer) {
    return new StyleToken("<i>", "</i>", false);
  }

  @Override protected @NotNull StyleToken formatBold(EnumSet<StringPrinter.Outer> outer) {
    return new StyleToken("<b>", "</b>", false);
  }

  @Override
  protected @NotNull StyleToken formatLineThrough(@NotNull Style.LineThrough line, EnumSet<StringPrinter.Outer> outer) {
    return new StyleToken("<span style=\"%s\">".formatted(styleToCss(line)), "</span>", false);
  }

  @Override protected @NotNull StyleToken formatColorHex(int rgb, boolean background) {
    return new StyleToken(
      "<span style=\"%s:%s;\">".formatted(background ? "background-color" : "color", cssColor(rgb)),
      "</span>",
      false
    );
  }

  public static class ClassedPreset extends Delegate {
    public ClassedPreset(@NotNull ClosingStylist delegate) {
      super(delegate);
    }

    @Override
    protected @NotNull ImmutableSeq<StyleToken> formatPresetStyle(@NotNull String styleName, EnumSet<StringPrinter.Outer> outer) {
      var className = styleKeyToCss(styleName).getLast();
      return ImmutableSeq.of(new StyleToken("<span class=\"%s\">".formatted(className), "</span>", false));
    }
  }

  /** @see org.aya.pretty.style.AyaStyleKey */
  public static @NotNull ImmutableSeq<String> styleKeyToCss(@NotNull String className) {
    return ImmutableSeq.from(className.split("::", -1)).map(Html5Stylist::normalizeCssId);
  }

  public static @Nullable String styleToCss(@NotNull Style style) {
    return switch (style) {
      case Style.Attr attr -> switch (attr) {
        case Italic -> "font-style: italic;";
        case Bold -> "font-weight: bold;";
      };
      case Style.LineThrough(var pos, var shape, var color) -> {
        var decoLine = switch (pos) {
          case Overline -> "text-decoration-line: overline;";
          case Underline -> "text-decoration-line: underline; text-underline-position: under;";
          case Strike -> "text-decoration-line: line-through;";
        };
        var decoStyle = switch (shape) {
          case Solid -> "text-decoration-style: solid;";
          case Curly -> "text-decoration-style: wavy;";
        };
        var colorRef = switch (color) {
          case Style.ColorHex(var rgb, _) -> cssColor(rgb);
          case Style.ColorName(var name, _) -> "var(%s)".formatted(cssVar(name));
          case null -> null;
        };
        var decoColor = colorRef != null
          ? "text-decoration-color: %s;".formatted(colorRef)
          : "";
        yield decoLine + decoStyle + decoColor;
      }
      case Style.ColorHex(var rgb, var background) -> background
        ? "background-color: %s".formatted(cssColor(rgb))
        : "color: %s;".formatted(cssColor(rgb));
      case Style.ColorName(var name, var background) -> background
        ? "background-color: var(%s);".formatted(cssVar(name))
        : "color: var(%s);".formatted(cssVar(name));
      default -> null;
    };
  }

  public static @NotNull String colorsToCss(@NotNull ColorScheme colorScheme) {
    return colorScheme.definedColors().toImmutableSeq().view()
      .map(t -> "%s: %s;".formatted(Html5Stylist.cssVar(t.component1()), Html5Stylist.cssColor(t.component2())))
      .joinToString("\n", "  %s"::formatted);
  }

  public static @NotNull String cssVar(@NotNull String name) {
    return STR."--\{normalizeCssId(name)}";
  }

  public static @NotNull String cssColor(int rgb) {
    return "#%06x".formatted(rgb);
  }

  /**
   * <a href="https://stackoverflow.com/a/45519999/9506898">Thank you!</a>
   * <a href="https://jkorpela.fi/ucs.html8">ISO 10646 character listings</a>
   * <p>
   * In CSS, identifiers (including element names, classes, and IDs in selectors)
   * can contain only the characters [a-zA-Z0-9] and ISO 10646 characters U+00A0 and higher,
   * plus the hyphen (-) and the underscore (_);
   * they cannot start with a digit, two hyphens, or a hyphen followed by a digit.
   * Identifiers can also contain escaped characters and any ISO 10646 character as a numeric code (see next item).
   * For instance, the identifier {@code B&W?} may be written as
   * {@code B\&W\?} or {@code B\26 W\3F}.
   */
  public static @NotNull String normalizeCssId(@NotNull String selector) {
    selector = selector.replaceAll("::", "-"); // note: scope::name -> scope-name
    // Java's `Pattern` assumes only ASCII text are matched, where `T²` will be incorrectly normalize to "T?".
    // But according to CSS3, `T²` is a valid identifier.
    var builder = new StringBuilder();
    for (var c : selector.toCharArray()) {
      if (Character.isLetterOrDigit(c) || c >= 0x00A0 || c == '-' || c == '_')
        builder.append(c);
      else builder.append(Integer.toHexString(c));
    }
    return builder.toString();
  }
}
