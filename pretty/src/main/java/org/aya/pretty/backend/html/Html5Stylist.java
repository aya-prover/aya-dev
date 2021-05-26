// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.backend.html;

import org.aya.pretty.backend.string.style.ClosingStylist;
import org.aya.pretty.doc.Style;
import org.glavo.kala.tuple.Tuple;
import org.glavo.kala.tuple.Tuple2;
import org.jetbrains.annotations.NotNull;

public class Html5Stylist extends ClosingStylist {
  @Override protected Tuple2<String, String> formatItalic() {
    return Tuple.of("<i>", "</i>");
  }

  @Override protected Tuple2<String, String> formatBold() {
    return Tuple.of("<b>", "</b>");
  }

  @Override protected Tuple2<String, String> formatStrike() {
    return Tuple.of("<s>", "</s>");
  }

  @Override protected Tuple2<String, String> formatCode() {
    return Tuple.of("<code>", "</code>");
  }

  @Override protected Tuple2<String, String> formatUnderline() {
    return Tuple.of("<u>", "</u>");
  }

  @Override protected Tuple2<String, String> formatColorHex(int rgb, boolean background) {
    return Tuple.of(
      "<span style=\"%s:#%06x;\">".formatted(background ? "background-color" : "color", rgb),
      "</span>"
    );
  }

  @Override protected @NotNull Tuple2<String, String> formatCustom(Style.@NotNull CustomStyle style) {
    // TODO: html custom style?
    return Tuple.of("", "");
  }
}
