// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.color;

import org.aya.pretty.printer.ColorScheme;
import org.glavo.kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.NotNull;

/**
 * The colors are from Emacs.
 */
public record EmacsColorScheme(
  @NotNull MutableMap<String, Integer> definedColors
) implements ColorScheme {
  public static final EmacsColorScheme INSTANCE = new EmacsColorScheme(MutableMap.of(
    "aya:Keyword", ColorScheme.colorOf(1.0f, 0.43f, 0),
    "aya:FnCall", ColorScheme.colorOf(0, 0, 1f),
    "aya:DataCall", ColorScheme.colorOf(0.13f, 0.55f, 0.13f),
    "aya:StructCall", ColorScheme.colorOf(0.13f, 0.55f, 0.13f),
    "aya:ConCall", ColorScheme.colorOf(0.63f, 0.13f, 0.94f)
  ));
}
