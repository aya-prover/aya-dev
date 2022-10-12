// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.style;

import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import org.aya.pretty.backend.string.custom.UnixTermStyle;
import org.aya.pretty.doc.Style;
import org.aya.pretty.doc.Styles;
import org.aya.pretty.printer.StyleFamily;
import org.jetbrains.annotations.NotNull;

public record AyaStyleFamily(
  @NotNull MutableMap<String, Styles> definedStyles
) implements StyleFamily {
  public enum Key {
    Keyword("aya:Keyword"),
    PrimCall("aya:PrimCall"),
    FnCall("aya:FnCall"),
    DataCall("aya:DataCall"),
    StructCall("aya:StructCall"),
    ConCall("aya:ConCall"),
    FieldCall("aya:FieldCall"),
    Generalized("aya:Generalized");

    private final String key;

    Key(String key) {
      this.key = key;
    }

    public String key() {
      return key;
    }
  }

  public static final @NotNull AyaStyleFamily DEFAULT = new AyaStyleFamily(MutableMap.ofEntries(
    Tuple.of(Key.Keyword.key(), Style.bold().and().color("aya:Keyword")),
    Tuple.of(Key.PrimCall.key(), Style.color("aya:Keyword").and()),
    Tuple.of(Key.FnCall.key(), Style.color("aya:FnCall").and()),
    Tuple.of(Key.DataCall.key(), Style.color("aya:DataCall").and()),
    Tuple.of(Key.StructCall.key(), Style.color("aya:StructCall").and()),
    Tuple.of(Key.ConCall.key(), Style.color("aya:ConCall").and()),
    Tuple.of(Key.FieldCall.key(), Style.color("aya:FieldCall").and()),
    Tuple.of(Key.Generalized.key(), Style.color("aya:Generalized").and())
  ));

  /** use colors from terminal instead of absolute colors to protect eyes */
  public static final @NotNull StyleFamily ADAPTIVE_CLI = new AyaStyleFamily(MutableMap.ofEntries(
    Tuple.of(Key.Keyword.key(), Style.color("aya:Keyword").and()),
    Tuple.of(Key.PrimCall.key(), Style.color("aya:Keyword").and()),
    Tuple.of(Key.FnCall.key(), UnixTermStyle.TerminalYellow.and()),
    Tuple.of(Key.DataCall.key(), UnixTermStyle.TerminalGreen.and()),
    Tuple.of(Key.StructCall.key(), UnixTermStyle.TerminalGreen.and()),
    Tuple.of(Key.ConCall.key(), UnixTermStyle.TerminalBlue.and()),
    Tuple.of(Key.FieldCall.key(), UnixTermStyle.TerminalBlue.and()),
    Tuple.of(Key.Generalized.key(), Style.italic().and())
  ));
}
