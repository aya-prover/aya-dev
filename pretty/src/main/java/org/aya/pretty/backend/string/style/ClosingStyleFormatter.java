// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.backend.string.style;

import org.aya.pretty.backend.string.StringStyleFormatter;
import org.aya.pretty.doc.Style;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.SeqView;
import org.glavo.kala.tuple.Tuple2;
import org.jetbrains.annotations.NotNull;

public abstract class ClosingStyleFormatter implements StringStyleFormatter {
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
    } else if (style instanceof Style.ColorBG bg) {
      // TODO[kiva]: support color name
      return formatTrueColor(bg.colorKey, true);
    } else if (style instanceof Style.ColorFG fg) {
      // TODO[kiva]: support color name
      return formatTrueColor(fg.colorKey, false);
    }

    throw new IllegalArgumentException("Unsupported style: " + style.getClass().getName());
  }

  protected abstract Tuple2<String, String> formatItalic();
  protected abstract Tuple2<String, String> formatBold();
  protected abstract Tuple2<String, String> formatStrike();
  protected abstract Tuple2<String, String> formatUnderline();

  protected abstract Tuple2<String, String> formatTrueColor(@NotNull String rgb, boolean background);
}
