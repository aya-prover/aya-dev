// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.backend.latex;

import org.aya.pretty.backend.string.style.ClosingStylist;
import org.aya.pretty.doc.Style;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.jetbrains.annotations.NotNull;

public class TeXStylist extends ClosingStylist {
  @Override protected Tuple2<String, String> formatItalic() {
    return Tuple.of("\\textit{", "}");
  }

  @Override protected Tuple2<String, String> formatBold() {
    return Tuple.of("\\textbf{", "}");
  }

  @Override protected Tuple2<String, String> formatStrike() {
    return Tuple.of("\\sout{", "}");
  }

  @Override protected Tuple2<String, String> formatUnderline() {
    return Tuple.of("\\underline{", "}");
  }

  @Override protected Tuple2<String, String> formatCode() {
    return Tuple.of("\\fbox{", "}");
  }

  @Override protected Tuple2<String, String> formatColorHex(int rgb, boolean background) {
    return Tuple.of("\\%s[HTML]{%06x}{".formatted(
      background ? "colorbox" : "textcolor", rgb), "}");
  }

  @Override protected @NotNull Tuple2<String, String> formatCustom(Style.@NotNull CustomStyle style) {
    return Tuple.of("", "");
  }
}
