// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.backend.string.style;

import org.aya.pretty.backend.string.StringStylist;
import org.aya.pretty.doc.Style;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.SeqView;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.collection.mutable.MutableMap;
import org.glavo.kala.control.Option;
import org.glavo.kala.tuple.Tuple;
import org.glavo.kala.tuple.Tuple2;
import org.jetbrains.annotations.NotNull;

public abstract class ClosingStylist implements StringStylist {
  private final MutableMap<String, Integer> definedColors = MutableHashMap.of();

  @Override
  public MutableMap<String, Integer> getColorScheme() {
    return definedColors;
  }

  public void format(@NotNull Seq<Style> styles, @NotNull StringBuilder builder, @NotNull Runnable inside) {
    formatInternal(styles.view(), builder, inside);
  }

  private void formatInternal(@NotNull SeqView<Style> styles, @NotNull StringBuilder builder, @NotNull Runnable inside) {
    if (styles.isEmpty()) {
      inside.run();
      return;
    }

    var style = styles.first();
    var format = formatOne(style);
    builder.append(format._1);
    formatInternal(styles.drop(1), builder, inside);
    builder.append(format._2);
  }

  protected @NotNull Tuple2<String, String> formatOne(Style style) {
    if (style instanceof Style.Attr attr) {
      return switch (attr) {
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
    return definedColors.getOption(colorName);
  }

  protected @NotNull Tuple2<String, String> formatColorName(@NotNull Style.ColorName color, boolean background) {
    var rgb = getColor(color.colorName());
    return rgb.isDefined()
      ? formatColorHex(rgb.get(), background)
      : Tuple.of("", "");
  }

  protected abstract Tuple2<String, String> formatItalic();
  protected abstract Tuple2<String, String> formatBold();
  protected abstract Tuple2<String, String> formatStrike();
  protected abstract Tuple2<String, String> formatUnderline();
  protected abstract Tuple2<String, String> formatColorHex(int rgb, boolean background);
  protected abstract @NotNull Tuple2<String, String> formatCustom(@NotNull Style.CustomStyle style);
}
