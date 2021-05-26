// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.backend.string.style;

import org.aya.pretty.backend.string.custom.UnixTermStyle;
import org.aya.pretty.doc.Style;
import org.glavo.kala.tuple.Tuple;
import org.glavo.kala.tuple.Tuple2;
import org.jetbrains.annotations.NotNull;

public class UnixTermStylist extends ClosingStylist {
  public static final @NotNull UnixTermStylist INSTANCE = new UnixTermStylist();

  private UnixTermStylist() {
  }

  @Override protected Tuple2<String, String> formatItalic() {
    return Tuple.of("\033[3m", "\033[23m");
  }

  @Override protected Tuple2<String, String> formatCode() {
    return Tuple.of("`", "'");
  }

  @Override protected Tuple2<String, String> formatBold() {
    return Tuple.of("\033[1m", "\033[22m");
  }

  @Override protected Tuple2<String, String> formatStrike() {
    return Tuple.of("\033[9m", "\033[29m");
  }

  @Override protected Tuple2<String, String> formatUnderline() {
    return Tuple.of("\033[4m", "\033[24m");
  }

  @Override
  protected @NotNull Tuple2<String, String> formatCustom(Style.@NotNull CustomStyle style) {
    if (style instanceof UnixTermStyle termStyle) {
      return switch (termStyle) {
        case Dim -> Tuple.of("\033[2m", "\033[22m");
        case DoubleUnderline -> Tuple.of("\033[21m", "\033[24m");
        case CurlyUnderline -> Tuple.of("\033[4:3m", "\033[4:0m");
        case Overline -> Tuple.of("\033[53m", "\033[55m");
        case Blink -> Tuple.of("\033[5m", "\033[25m");
        case Reverse -> Tuple.of("\033[7m", "\033[27m");
      };
    }
    return Tuple.of("", "");
  }

  @Override protected @NotNull Tuple2<String, String> formatColorHex(int rgb, boolean bg) {
    int r = (rgb & 0xFF0000) >> 16;
    int g = (rgb & 0xFF00) >> 8;
    int b = (rgb & 0xFF);

    return Tuple.of(
      String.format("\033[%d;2;%d;%d;%dm", bg ? 48 : 38, r, g, b),
      String.format("\033[%dm", bg ? 49 : 39)
    );
  }
}
