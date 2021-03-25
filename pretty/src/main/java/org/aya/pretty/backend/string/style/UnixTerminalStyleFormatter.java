// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.backend.string.style;

import org.glavo.kala.tuple.Tuple;
import org.glavo.kala.tuple.Tuple2;
import org.jetbrains.annotations.NotNull;

public class UnixTerminalStyleFormatter extends ClosingStyleFormatter {
  public static final UnixTerminalStyleFormatter INSTANCE = new UnixTerminalStyleFormatter();

  @Override
  protected Tuple2<String, String> formatItalic() {
    return Tuple.of("\033[3m", "\033[23m");
  }

  @Override
  protected Tuple2<String, String> formatBold() {
    return Tuple.of("\033[1m", "\033[22m");
  }

  @Override
  protected Tuple2<String, String> formatStrike() {
    return Tuple.of("\033[9m", "\033[29m");
  }

  @Override
  protected Tuple2<String, String> formatUnderline() {
    return Tuple.of("\033[4m", "\033[24m");
  }

  @Override
  protected @NotNull Tuple2<String, String> formatTrueColor(@NotNull String rgb, boolean bg) {
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
