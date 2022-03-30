// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.printer;

import org.aya.pretty.style.AyaStyleFamily;
import org.aya.pretty.style.EmacsColorScheme;
import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
public abstract class Stylist {
  protected @NotNull ColorScheme colorScheme;
  protected @NotNull StyleFamily styleFamily;

  public Stylist() {
    this(EmacsColorScheme.INSTANCE, AyaStyleFamily.DEFAULT);
  }

  public Stylist(@NotNull ColorScheme colorScheme, @NotNull StyleFamily styleFamily) {
    this.colorScheme = colorScheme;
    this.styleFamily = styleFamily;
  }

  public void setStyleFamily(@NotNull StyleFamily styleFamily) {
    this.styleFamily = styleFamily;
  }
}
