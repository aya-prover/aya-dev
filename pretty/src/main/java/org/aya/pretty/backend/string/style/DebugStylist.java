// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.string.style;

import kala.collection.Seq;
import org.aya.pretty.backend.string.Cursor;
import org.aya.pretty.backend.string.StringStylist;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;

/**
 * @author kiva, ice1000
 */
public class DebugStylist extends StringStylist {
  public static final DebugStylist INSTANCE = new DebugStylist();

  private DebugStylist() {
  }

  @Override public void format(@NotNull Seq<Style> style, @NotNull Cursor cursor, @NotNull Runnable inside) {
    if (style.contains(Style.code())) {
      cursor.visibleContent("`");
      inside.run();
      cursor.visibleContent("`");
    } else inside.run();
  }
}
