// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.string;

import kala.collection.Seq;
import org.aya.pretty.doc.Style;
import org.aya.pretty.printer.ColorScheme;
import org.aya.pretty.printer.StyleFamily;
import org.aya.pretty.style.AyaColorScheme;
import org.aya.pretty.style.AyaStyleFamily;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

/**
 * @author kiva, ice1000
 */
public class DebugStylist extends StringStylist {
  public static final DebugStylist DEFAULT = new DebugStylist(AyaColorScheme.INTELLIJ, AyaStyleFamily.DEFAULT);

  public DebugStylist(@NotNull ColorScheme colorScheme, @NotNull StyleFamily styleFamily) {
    super(colorScheme, styleFamily);
  }

  @Override public void format(@NotNull Seq<Style> style, @NotNull Cursor cursor, EnumSet<StringPrinter.Outer> outer, @NotNull Runnable inside) {
    inside.run();
  }
}
