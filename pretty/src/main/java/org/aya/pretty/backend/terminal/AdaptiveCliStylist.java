// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.terminal;

import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import org.aya.pretty.doc.Style;
import org.aya.pretty.printer.ColorScheme;
import org.aya.pretty.style.AyaStyleFamily;
import org.jetbrains.annotations.NotNull;

/** use colors from terminal instead of absolute colors to protect eyes */
public class AdaptiveCliStylist extends UnixTermStylist {
  public static final @NotNull AdaptiveCliStylist INSTANCE = new AdaptiveCliStylist();

  private AdaptiveCliStylist() {
    super(MutableMap::create, new AyaStyleFamily(MutableMap.ofEntries(
      Tuple.of(AyaStyleFamily.Key.Keyword.key(), Style.color(ColorScheme.colorOf(1.0f, 0.43f, 0)).and()),
      Tuple.of(AyaStyleFamily.Key.PrimCall.key(), Style.color(ColorScheme.colorOf(1.0f, 0.43f, 0)).and()),
      Tuple.of(AyaStyleFamily.Key.FnCall.key(), UnixTermStyle.TerminalYellow.and()),
      Tuple.of(AyaStyleFamily.Key.DataCall.key(), UnixTermStyle.TerminalGreen.and()),
      Tuple.of(AyaStyleFamily.Key.StructCall.key(), UnixTermStyle.TerminalGreen.and()),
      Tuple.of(AyaStyleFamily.Key.ConCall.key(), UnixTermStyle.TerminalBlue.and()),
      Tuple.of(AyaStyleFamily.Key.FieldCall.key(), UnixTermStyle.TerminalBlue.and()),
      Tuple.of(AyaStyleFamily.Key.Generalized.key(), Style.italic().and())
    )));
  }
}
