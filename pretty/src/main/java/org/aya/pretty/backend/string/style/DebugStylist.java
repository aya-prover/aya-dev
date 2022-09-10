// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.string.style;

import kala.collection.Seq;
import org.aya.pretty.backend.string.Cursor;
import org.aya.pretty.backend.string.StringStylist;
import org.aya.pretty.doc.Style;
import org.aya.pretty.style.AyaColorScheme;
import org.aya.pretty.style.AyaStyleFamily;
import org.jetbrains.annotations.NotNull;

/**
 * @author kiva, ice1000
 */
public class DebugStylist extends StringStylist {
  public static final DebugStylist INSTANCE = new DebugStylist();

  private DebugStylist() {
    super(AyaColorScheme.INTELLIJ, AyaStyleFamily.DEFAULT);
  }

  @Override public void format(@NotNull Seq<Style> style, @NotNull Cursor cursor, @NotNull Runnable inside) {
    if (style.contains(Style.code())) {
      cursor.visibleContent("`");
      inside.run();
      cursor.visibleContent("`");
    } else inside.run();
  }
}
