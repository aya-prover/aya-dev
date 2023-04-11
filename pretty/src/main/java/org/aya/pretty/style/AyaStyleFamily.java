// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
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
    Tuple.of(AyaStyleKey.Clazz.key(), Style.color(AyaStyleKey.Clazz.key()).and()),
    Tuple.of(AyaStyleKey.Con.key(), Style.color(AyaStyleKey.Con.key()).and()),
    Tuple.of(AyaStyleKey.Member.key(), Style.color(AyaStyleKey.Member.key()).and()),
    Tuple.of(AyaStyleKey.Generalized.key(), Style.color(AyaStyleKey.Generalized.key()).and()),
    Tuple.of(AyaStyleKey.CallTerm.key(), Styles.empty()),
    Tuple.of(AyaStyleKey.Comment.key(), Style.color(AyaStyleKey.Comment.key()).and().italic()),
    Tuple.of(AyaStyleKey.LocalVar.key(), Style.italic().and()),
    Tuple.of(AyaStyleKey.Error.key(), new Style.LineThrough(
      Style.LineThrough.Position.Underline, Style.LineThrough.Shape.Curly,
      Style.color(AyaStyleKey.Error.key())
    ).and()),
    Tuple.of(AyaStyleKey.Warning.key(), new Style.LineThrough(
      Style.LineThrough.Position.Underline, Style.LineThrough.Shape.Curly,
      Style.color(AyaStyleKey.Warning.key())
    ).and()),
    Tuple.of(AyaStyleKey.Goal.key(), Style.colorBg(AyaStyleKey.Goal.key()).and())
  ));
}
