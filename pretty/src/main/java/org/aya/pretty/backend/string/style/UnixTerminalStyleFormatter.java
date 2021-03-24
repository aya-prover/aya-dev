// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.backend.string.style;

import org.aya.pretty.backend.string.StringStyleFormatter;
import org.aya.pretty.doc.Style;
import org.glavo.kala.collection.Seq;
import org.jetbrains.annotations.NotNull;

public class UnixTerminalStyleFormatter implements StringStyleFormatter {
  public static final UnixTerminalStyleFormatter INSTANCE = new UnixTerminalStyleFormatter();

  @Override
  public void format(@NotNull Seq<Style> styles, @NotNull StringBuilder builder, Runnable inside) {
    inside.run();
  }
}
