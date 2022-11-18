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
  public enum Key {
    Keyword("aya:Keyword"),
    FnCall("aya:FnCall"),
    Generalized("aya:Generalized"),
    DataCall("aya:DataCall"),
    StructCall("aya:StructCall"),
    ConCall("aya:ConCall"),
    FieldCall("aya:FieldCall");

    private final String key;

    Key(String key) {
      this.key = key;
    }

    public String key() {
      return key;
    }
  }

  public static final @NotNull AyaColorScheme EMACS = new AyaColorScheme(MutableMap.ofEntries(
    Tuple.of(Key.Keyword.key(), ColorScheme.colorOf(1.0f, 0.43f, 0)),
    Tuple.of(Key.FnCall.key(), ColorScheme.colorOf(0, 0, 1f)),
    Tuple.of(Key.Generalized.key(), ColorScheme.colorOf(0, 0, 1f)),
    Tuple.of(Key.DataCall.key(), ColorScheme.colorOf(0.13f, 0.55f, 0.13f)),
    Tuple.of(Key.StructCall.key(), ColorScheme.colorOf(0.13f, 0.55f, 0.13f)),
    Tuple.of(Key.ConCall.key(), ColorScheme.colorOf(0.63f, 0.13f, 0.94f)),
    Tuple.of(Key.FieldCall.key(), ColorScheme.colorOf(0, 0.55f, 0.55f))
  ));

  public static final @NotNull AyaColorScheme INTELLIJ = new AyaColorScheme(MutableMap.ofEntries(
    Tuple.of(Key.Keyword.key(), 0x0033B3),
    Tuple.of(Key.FnCall.key(), 0x00627A),
    Tuple.of(Key.Generalized.key(), 0x00627A),
    Tuple.of(Key.DataCall.key(), 0x000000),
    Tuple.of(Key.StructCall.key(), 0x000000),
    Tuple.of(Key.ConCall.key(), 0x067D17),
    Tuple.of(Key.FieldCall.key(), 0x871094)
  ));
}
