// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.string.style;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.control.Option;
import org.aya.pretty.backend.string.Cursor;
import org.aya.pretty.backend.string.StringStylist;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;

public abstract class ClosingStylist extends StringStylist {
  public record StyleToken(@NotNull String start, @NotNull String end, boolean visible) {
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

  protected @NotNull StyleToken formatOne(Style style) {
    if (style instanceof Style.Attr attr) {
      return switch (attr) {
        case Code -> formatCode();
        case Italic -> formatItalic();
        case Bold -> formatBold();
        case Strike -> formatStrike();
        case Underline -> formatUnderline();
      };
    } else if (style instanceof Style.ColorName color) {
      return formatColorName(color, color.background());
    } else if (style instanceof Style.ColorHex color) {
      return formatColorHex(color.color(), color.background());
    } else if (style instanceof Style.CustomStyle custom) {
      return formatCustom(custom);
    }

    throw new IllegalArgumentException("Unsupported style: " + style.getClass().getName());
  }

  private @NotNull Option<Integer> getColor(@NotNull String colorName) {
    return colorScheme.definedColors().getOption(colorName);
  }

  protected @NotNull SeqView<StyleToken> formatPreset(String styleName) {
    var style = styleFamily.definedStyles().getOption(styleName);
    if (style.isEmpty()) return SeqView.empty();
    return style.get().styles.view().map(this::formatOne);
  }

  protected @NotNull StyleToken formatColorName(@NotNull Style.ColorName color, boolean background) {
    var rgb = getColor(color.colorName());
    return rgb.isDefined()
      ? formatColorHex(rgb.get(), background)
      : StyleToken.NULL;
  }

  protected abstract StyleToken formatItalic();
  protected abstract StyleToken formatCode();
  protected abstract StyleToken formatBold();
  protected abstract StyleToken formatStrike();
  protected abstract StyleToken formatUnderline();
  protected abstract StyleToken formatColorHex(int rgb, boolean background);
  protected abstract @NotNull StyleToken formatCustom(@NotNull Style.CustomStyle style);
}
