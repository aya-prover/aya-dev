// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.backend.string;

import org.aya.pretty.doc.Style;
import org.aya.pretty.printer.Stylist;
import kala.collection.Seq;
import org.jetbrains.annotations.NotNull;

public abstract class StringStylist extends Stylist {
  public abstract void format(@NotNull Seq<Style> style, @NotNull StringBuilder builder, @NotNull Runnable inside);
}
