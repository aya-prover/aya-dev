// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.string.style;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.control.Option;
import org.aya.pretty.backend.string.Cursor;
import org.aya.pretty.backend.string.StringStylist;
import org.aya.pretty.doc.Style;
import org.aya.pretty.printer.ColorScheme;
import org.aya.pretty.printer.StyleFamily;
import org.jetbrains.annotations.NotNull;

public abstract class ClosingStylist extends StringStylist {
  public ClosingStylist(@NotNull ColorScheme colorScheme, @NotNull StyleFamily styleFamily) {
    super(colorScheme, styleFamily);
  }

  public record StyleToken(@NotNull CharSequence start, @NotNull CharSequence end, boolean visible) {
    public static final @NotNull StyleToken NULL = new StyleToken("", "", false);
  }

  @Override
  public void format(@NotNull Seq<Style> styles, @NotNull Cursor cursor, @NotNull Runnable inside) {
    formatInternal(styles.view(), cursor, inside);
  }

  private void formatInternal(@NotNull SeqView<Style> styles, @NotNull Cursor cursor, @NotNull Runnable inside) {
    if (styles.isEmpty()) {
      inside.run();
      return;
    }

    var style = styles.first();
    if (style instanceof Style.Preset preset) {
      formatMany(styles, cursor, inside, formatPreset(preset.styleName()));
    } else {
      formatMany(styles, cursor, inside, SeqView.of(formatOne(style)));
    }
  }

  private void formatMany(@NotNull SeqView<Style> styles, @NotNull Cursor cursor,
                          @NotNull Runnable inside,
                          @NotNull SeqView<StyleToken> formats) {
    formats.forEach(format -> cursor.content(format.start, format.visible));
    formatInternal(styles.drop(1), cursor, inside);
    formats.reversed().forEach(format -> cursor.content(format.end, format.visible));
  }

  protected @NotNull StyleToken formatOne(@NotNull Style style) {
    return switch (style) {
      case Style.Attr attr -> switch (attr) {
        case Italic -> formatItalic();
        case Bold -> formatBold();
        case Strike -> formatStrike();
        case Underline -> formatUnderline();
      };
      case Style.CodeBlock code -> formatInlineCode(code.language());
      case Style.InlineCode code -> formatCodeBlock(code.language());
      case Style.ColorName color -> formatColorName(color, color.background());
      case Style.ColorHex hex -> formatColorHex(hex.color(), hex.background());
      case Style.CustomStyle custom -> formatCustom(custom);
      default -> StyleToken.NULL;
    };
  }

  private @NotNull Option<Integer> getColor(@NotNull String colorName) {
    return colorScheme.definedColors().getOption(colorName);
  }

  protected @NotNull SeqView<StyleToken> formatPreset(String styleName) {
    var style = styleFamily.definedStyles().getOption(styleName);
    if (style.isEmpty()) return SeqView.empty();
    return style.get().styles().view().map(this::formatOne);
  }

  protected @NotNull StyleToken formatColorName(@NotNull Style.ColorName color, boolean background) {
    return getColor(color.colorName()).getOrDefault(it -> formatColorHex(it, background), StyleToken.NULL);
  }

  protected @NotNull StyleToken formatCustom(@NotNull Style.CustomStyle style) {
    return StyleToken.NULL;
  }

  protected @NotNull StyleToken formatCodeBlock(@NotNull String language) {
    return formatInlineCode(language);
  }

  protected abstract @NotNull StyleToken formatInlineCode(@NotNull String language);
  protected abstract @NotNull StyleToken formatItalic();
  protected abstract @NotNull StyleToken formatBold();
  protected abstract @NotNull StyleToken formatStrike();
  protected abstract @NotNull StyleToken formatUnderline();
  protected abstract @NotNull StyleToken formatColorHex(int rgb, boolean background);
}
