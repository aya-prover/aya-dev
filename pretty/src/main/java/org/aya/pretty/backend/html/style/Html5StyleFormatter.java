// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.backend.html.style;

import org.aya.pretty.backend.string.style.ClosingStyleFormatter;
import org.glavo.kala.tuple.Tuple;
import org.glavo.kala.tuple.Tuple2;
import org.jetbrains.annotations.NotNull;

public class Html5StyleFormatter extends ClosingStyleFormatter {
  @Override
  protected Tuple2<String, String> formatItalic() {
    return Tuple.of("<em>", "</em>");
  }

  @Override
  protected Tuple2<String, String> formatBold() {
    return Tuple.of("<strong>", "</strong>");
  }

  @Override
  protected Tuple2<String, String> formatStrike() {
    return Tuple.of("<del>", "</del>");
  }

  @Override
  protected Tuple2<String, String> formatUnderline() {
    return Tuple.of("<u>", "</u>");
  }

  @Override
  protected Tuple2<String, String> formatTrueColor(@NotNull String rgb, boolean background) {
    return background
      ? Tuple.of(String.format("<p style=\"display: inline;\"><span style=\"background-color:%s;\">", rgb), "</span></p>")
      : Tuple.of(String.format("<font color=\"%s\">", rgb), "</font>");
  }
}
