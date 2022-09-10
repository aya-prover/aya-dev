// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.style;

import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import org.aya.pretty.printer.ColorScheme;
import org.jetbrains.annotations.NotNull;

/**
 * The colors are from Emacs.
 */
public record AyaColorScheme(
  @NotNull MutableMap<String, Integer> definedColors
) implements ColorScheme {
  public static final @NotNull AyaColorScheme EMACS = new AyaColorScheme(MutableMap.ofEntries(
    Tuple.of("aya:Keyword", ColorScheme.colorOf(1.0f, 0.43f, 0)),
    Tuple.of("aya:FnCall", ColorScheme.colorOf(0, 0, 1f)),
    Tuple.of("aya:Generalized", ColorScheme.colorOf(0, 0, 1f)),
    Tuple.of("aya:DataCall", ColorScheme.colorOf(0.13f, 0.55f, 0.13f)),
    Tuple.of("aya:StructCall", ColorScheme.colorOf(0.13f, 0.55f, 0.13f)),
    Tuple.of("aya:ConCall", ColorScheme.colorOf(0.63f, 0.13f, 0.94f)),
    Tuple.of("aya:FieldCall", ColorScheme.colorOf(0, 0.55f, 0.55f))
  ));

  public static final @NotNull AyaColorScheme INTELLIJ = new AyaColorScheme(MutableMap.ofEntries(
    Tuple.of("aya:Keyword", 0x0033B3),
    Tuple.of("aya:FnCall", 0x00627A),
    Tuple.of("aya:Generalized", 0x00627A),
    Tuple.of("aya:DataCall", 0x000000),
    Tuple.of("aya:StructCall", 0x000000),
    Tuple.of("aya:ConCall", 0x067D17),
    Tuple.of("aya:FieldCall", 0x871094)
  ));
}
