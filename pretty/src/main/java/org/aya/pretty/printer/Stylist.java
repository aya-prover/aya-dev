// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.printer;

import org.aya.pretty.color.EmacsColorScheme;

public abstract class Stylist {
  protected ColorScheme colorScheme;

  public Stylist() {
    this(EmacsColorScheme.INSTANCE);
  }

  public Stylist(ColorScheme colorScheme) {
    this.colorScheme = colorScheme;
  }
}
