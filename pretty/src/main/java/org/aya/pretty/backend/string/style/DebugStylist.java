// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.backend.string.style;

import org.aya.pretty.backend.string.StringStylist;
import org.aya.pretty.doc.Style;
import kala.collection.Seq;
import org.jetbrains.annotations.NotNull;

/**
 * @author kiva, ice1000
 */
public class DebugStylist extends StringStylist {
  public static final DebugStylist INSTANCE = new DebugStylist();

  private DebugStylist() {
  }

  @Override public void format(@NotNull Seq<Style> style, @NotNull StringBuilder builder, @NotNull Runnable inside) {
    if (style.contains(Style.code())) {
      builder.append("`");
      inside.run();
      builder.append("`");
    } else inside.run();
  }
}
