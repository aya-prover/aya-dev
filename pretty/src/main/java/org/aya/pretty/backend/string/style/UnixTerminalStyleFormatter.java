// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.backend.string.style;

import org.aya.pretty.backend.string.StringStyleFormatter;
import org.aya.pretty.doc.Style;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.SeqView;
import org.glavo.kala.tuple.Tuple;
import org.glavo.kala.tuple.Tuple2;
import org.jetbrains.annotations.NotNull;

public class UnixTerminalStyleFormatter implements StringStyleFormatter {
  public static final UnixTerminalStyleFormatter INSTANCE = new UnixTerminalStyleFormatter();

  @Override
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

  private @NotNull Tuple2<String, String> formatOne(Style style) {
    if (style instanceof Style.Attr attr) {
      return switch (attr) {
        case Italic -> Tuple.of("\033[3m", "\033[23m");
        case Bold -> Tuple.of("\033[1m", "\033[22m");
        case Strike -> Tuple.of("\033[9m", "\033[29m");
        case Underline -> Tuple.of("\033[4m", "\033[24m");
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

  private @NotNull Tuple2<String, String> formatTrueColor(@NotNull String rgb, boolean bg) {
    if (!rgb.startsWith("#")) {
      // invalid color
      return Tuple.of("", "");
    }

    int hex = Integer.parseInt(rgb.replace("#", ""), 16);
    int r = (hex & 0xFF0000) >> 16;
    int g = (hex & 0xFF00) >> 8;
    int b = (hex & 0xFF);

    // \033[${bg ? 48 : 38};2;${red};${green};${blue}m ${text} \033[49m
    return Tuple.of(
      String.format("\033[%d;2;%d;%d;%dm", bg ? 48 : 38, r, g, b),
      String.format("\033[%dm", bg ? 49 : 39)
    );
  }
}
