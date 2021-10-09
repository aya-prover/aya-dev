// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.printer;

import org.aya.pretty.style.AyaStyleFamily;
import org.aya.pretty.style.EmacsColorScheme;

/**
 * @author kiva
 */
public abstract class Stylist {
  protected final ColorScheme colorScheme;
  protected final StyleFamily styleFamily;

  public Stylist() {
    this(EmacsColorScheme.INSTANCE, AyaStyleFamily.INSTANCE);
  }

  public Stylist(ColorScheme colorScheme, StyleFamily styleFamily) {
    this.colorScheme = colorScheme;
    this.styleFamily = styleFamily;
  }
}
