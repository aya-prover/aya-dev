// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
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
  public static final @NotNull AyaStyleFamily DEFAULT = new AyaStyleFamily(MutableMap.ofEntries(
    Tuple.of("aya:Keyword", Style.bold().and().color("aya:Keyword")),
    Tuple.of("aya:FnCall", Style.color("aya:FnCall").and()),
    Tuple.of("aya:DataCall", Style.color("aya:DataCall").and()),
    Tuple.of("aya:StructCall", Style.color("aya:StructCall").and()),
    Tuple.of("aya:ConCall", Style.color("aya:ConCall").and()),
    Tuple.of("aya:FieldCall", Style.color("aya:FieldCall").and()),
    Tuple.of("aya:Generalized", Style.color("aya:Generalized").and())
  ));

  /** use colors from terminal instead of absolute colors to protect eyes */
  public static final @NotNull StyleFamily ADAPTIVE_CLI = new AyaStyleFamily(MutableMap.ofEntries(
    Tuple.of("aya:Keyword", Style.color("aya:Keyword").and()),
    Tuple.of("aya:FnCall", UnixTermStyle.TerminalYellow.and()),
    Tuple.of("aya:DataCall", UnixTermStyle.TerminalGreen.and()),
    Tuple.of("aya:StructCall", UnixTermStyle.TerminalGreen.and()),
    Tuple.of("aya:ConCall", UnixTermStyle.TerminalBlue.and()),
    Tuple.of("aya:FieldCall", UnixTermStyle.TerminalBlue.and()),
    Tuple.of("aya:Generalized", Style.italic().and())
  ));
}
