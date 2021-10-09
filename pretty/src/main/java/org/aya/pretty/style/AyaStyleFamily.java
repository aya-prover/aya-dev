// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.style;

import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import org.aya.pretty.doc.Style;
import org.aya.pretty.doc.Styles;
import org.aya.pretty.printer.StyleFamily;
import org.jetbrains.annotations.NotNull;

public record AyaStyleFamily(
  @NotNull MutableMap<String, Styles> definedStyles
) implements StyleFamily {
  public static final AyaStyleFamily INSTANCE = new AyaStyleFamily(MutableMap.ofEntries(
    Tuple.of("aya:Keyword", Style.bold().and().color("aya:Keyword")),
    Tuple.of("aya:FnCall", Style.color("aya:FnCall").and()),
    Tuple.of("aya:DataCall", Style.color("aya:DataCall").and()),
    Tuple.of("aya:StructCall", Style.color("aya:StructCall").and()),
    Tuple.of("aya:ConCall", Style.color("aya:ConCall").and()),
    Tuple.of("aya:FieldCall", Style.color("aya:FieldCall").and()),
    Tuple.of("aya:Generalized", Style.color("aya:Generalized").and())
  ));
}
