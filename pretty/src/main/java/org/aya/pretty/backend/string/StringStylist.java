// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.string;

import kala.collection.Seq;
import org.aya.pretty.doc.Style;
import org.aya.pretty.printer.Stylist;
import org.jetbrains.annotations.NotNull;

public abstract class StringStylist extends Stylist {
  public abstract void format(@NotNull Seq<Style> style, @NotNull Cursor cursor, @NotNull Runnable inside);
}
