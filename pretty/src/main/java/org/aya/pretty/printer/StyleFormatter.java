// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.printer;

import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;

public interface StyleFormatter {
  String format(@NotNull Style style);

  class IgnoringFormatter implements StyleFormatter {
    @Override
    public String format(@NotNull Style style) {
      return "";
    }
  }
}
