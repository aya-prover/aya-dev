// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.style;

import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import org.aya.pretty.doc.Style;
import org.aya.pretty.doc.Styles;
import org.aya.pretty.printer.StyleFamily;
import org.jetbrains.annotations.NotNull;

public record AyaStyleFamily(@NotNull MutableMap<String, Styles> definedStyles) implements StyleFamily {
  public static final @NotNull AyaStyleFamily DEFAULT = new AyaStyleFamily(MutableMap.ofEntries(
    Tuple.of(AyaStyleKey.Keyword.key(), Style.bold().and().color(AyaStyleKey.Keyword.key())),
    Tuple.of(AyaStyleKey.Prim.key(), Style.color(AyaStyleKey.Prim.key()).and()),
    Tuple.of(AyaStyleKey.Fn.key(), Style.color(AyaStyleKey.Fn.key()).and()),
    Tuple.of(AyaStyleKey.Data.key(), Style.color(AyaStyleKey.Data.key()).and()),
    Tuple.of(AyaStyleKey.Struct.key(), Style.color(AyaStyleKey.Struct.key()).and()),
    Tuple.of(AyaStyleKey.Con.key(), Style.color(AyaStyleKey.Con.key()).and()),
    Tuple.of(AyaStyleKey.Field.key(), Style.color(AyaStyleKey.Field.key()).and()),
    Tuple.of(AyaStyleKey.Generalized.key(), Style.color(AyaStyleKey.Generalized.key()).and()),
    Tuple.of(AyaStyleKey.CallTerm.key(), Styles.empty()),
    Tuple.of(AyaStyleKey.Comment.key(), Style.color(AyaStyleKey.Comment.key()).and().italic())
  ));
}
