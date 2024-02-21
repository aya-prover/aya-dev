// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.terminal;

import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import org.aya.pretty.doc.Style;
import org.aya.pretty.printer.ColorScheme;
import org.aya.pretty.style.AyaStyleFamily;
import org.aya.pretty.style.AyaStyleKey;
import org.jetbrains.annotations.NotNull;

/** use colors from terminal instead of absolute colors to protect eyes */
public class AdaptiveCliStylist extends UnixTermStylist {
  public static final @NotNull AdaptiveCliStylist INSTANCE = new AdaptiveCliStylist(true);
  public static final @NotNull AdaptiveCliStylist INSTANCE_16 = new AdaptiveCliStylist(false);

  private AdaptiveCliStylist(boolean use256Colors) {
    super(MutableMap::create, new AyaStyleFamily(MutableMap.ofEntries(
      Tuple.of(AyaStyleKey.Keyword.key(), (use256Colors ? Style.color(ColorScheme.colorOf(1.0f, 0.43f, 0)) : UnixTermStyle.TerminalRed).and()),
      Tuple.of(AyaStyleKey.Prim.key(), (use256Colors ? Style.color(ColorScheme.colorOf(1.0f, 0.43f, 0)) : UnixTermStyle.TerminalRed).and()),
      Tuple.of(AyaStyleKey.Fn.key(), UnixTermStyle.TerminalYellow.and()),
      Tuple.of(AyaStyleKey.Data.key(), UnixTermStyle.TerminalGreen.and()),
      Tuple.of(AyaStyleKey.Clazz.key(), UnixTermStyle.TerminalGreen.and()),
      Tuple.of(AyaStyleKey.Con.key(), UnixTermStyle.TerminalBlue.and()),
      Tuple.of(AyaStyleKey.Member.key(), UnixTermStyle.TerminalBlue.and()),
      Tuple.of(AyaStyleKey.Generalized.key(), Style.italic().and()),
      Tuple.of(AyaStyleKey.Comment.key(), Style.italic().and()),
      Tuple.of(AyaStyleKey.LocalVar.key(), Style.italic().and())
    )));
  }
}
