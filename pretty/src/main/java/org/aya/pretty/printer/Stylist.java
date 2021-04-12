// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.printer;

import org.aya.pretty.style.AyaStyleFamily;
import org.aya.pretty.style.EmacsColorScheme;

public abstract class Stylist {
  protected ColorScheme colorScheme;
  protected StyleFamily styleFamily;

  public Stylist() {
    this(EmacsColorScheme.INSTANCE, AyaStyleFamily.INSTANCE);
  }

  public Stylist(ColorScheme colorScheme, StyleFamily styleFamily) {
    this.colorScheme = colorScheme;
    this.styleFamily = styleFamily;
  }
}
