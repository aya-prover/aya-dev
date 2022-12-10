// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.style;

import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import org.aya.pretty.printer.ColorScheme;
import org.jetbrains.annotations.NotNull;

public record AyaColorScheme(@NotNull MutableMap<String, Integer> definedColors) implements ColorScheme {
  /** The colors are from Emacs. */
  public static final @NotNull AyaColorScheme EMACS = new AyaColorScheme(MutableMap.ofEntries(
    Tuple.of(AyaStyleKey.Keyword.key(), ColorScheme.colorOf(1.0f, 0.43f, 0)),
    Tuple.of(AyaStyleKey.Fn.key(), ColorScheme.colorOf(0, 0, 1f)),
    Tuple.of(AyaStyleKey.Prim.key(), ColorScheme.colorOf(0, 0, 1f)),
    Tuple.of(AyaStyleKey.Generalized.key(), ColorScheme.colorOf(0, 0, 1f)),
    Tuple.of(AyaStyleKey.Data.key(), ColorScheme.colorOf(0.13f, 0.55f, 0.13f)),
    Tuple.of(AyaStyleKey.Struct.key(), ColorScheme.colorOf(0.13f, 0.55f, 0.13f)),
    Tuple.of(AyaStyleKey.Con.key(), ColorScheme.colorOf(0.63f, 0.13f, 0.94f)),
    Tuple.of(AyaStyleKey.Field.key(), ColorScheme.colorOf(0, 0.55f, 0.55f))
  ));

  /** The colors are from IntelliJ IDEA light theme. */
  public static final @NotNull AyaColorScheme INTELLIJ = new AyaColorScheme(MutableMap.ofEntries(
    Tuple.of(AyaStyleKey.Keyword.key(), 0x0033B3),
    Tuple.of(AyaStyleKey.Fn.key(), 0x00627A),
    Tuple.of(AyaStyleKey.Prim.key(), 0x00627A),
    Tuple.of(AyaStyleKey.Generalized.key(), 0x00627A),
    Tuple.of(AyaStyleKey.Data.key(), 0x000000),
    Tuple.of(AyaStyleKey.Struct.key(), 0x000000),
    Tuple.of(AyaStyleKey.Con.key(), 0x067D17),
    Tuple.of(AyaStyleKey.Field.key(), 0x871094)
  ));
}
