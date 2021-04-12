// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.backend.string.style;

import org.aya.pretty.backend.string.StringStylist;
import org.aya.pretty.doc.Style;
import org.glavo.kala.collection.Seq;
import org.jetbrains.annotations.NotNull;

public class IgnoringStylist extends StringStylist {
  public static final IgnoringStylist INSTANCE = new IgnoringStylist();

  public IgnoringStylist() {
  }

  @Override public void format(@NotNull Seq<Style> style, @NotNull StringBuilder builder, @NotNull Runnable inside) {
    inside.run();
  }
}
