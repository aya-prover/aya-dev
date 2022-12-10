// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.printer;

import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
public abstract class Stylist {
  /** predefined colors, see {@link org.aya.pretty.doc.Style#color(String)} and {@link org.aya.pretty.doc.Style#colorBg(String)} */
  public @NotNull ColorScheme colorScheme;
  /** predefined styles, see {@link org.aya.pretty.doc.Style#preset(String)} */
  public @NotNull StyleFamily styleFamily;

  public Stylist(@NotNull ColorScheme colorScheme, @NotNull StyleFamily styleFamily) {
    this.colorScheme = colorScheme;
    this.styleFamily = styleFamily;
  }
}
