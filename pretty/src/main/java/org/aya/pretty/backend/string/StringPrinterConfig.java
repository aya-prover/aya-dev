// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.string;

import org.aya.pretty.backend.string.style.UnixTermStylist;
import org.aya.pretty.printer.ColorScheme;
import org.aya.pretty.printer.PrinterConfig;
import org.aya.pretty.printer.StyleFamily;
import org.aya.pretty.style.AyaColorScheme;
import org.aya.pretty.style.AyaStyleFamily;
import org.jetbrains.annotations.NotNull;

public class StringPrinterConfig extends PrinterConfig.Basic {
  public final boolean unicode;

  public StringPrinterConfig(@NotNull StringStylist stylist, int pageWidth, boolean unicode) {
    super(pageWidth, INFINITE_SIZE, stylist);
    this.unicode = unicode;
  }

  @Override public @NotNull StringStylist getStylist() {
    return (StringStylist) super.getStylist();
  }

  public static @NotNull StringPrinterConfig unixTerminal(
    @NotNull ColorScheme colorScheme, @NotNull StyleFamily styleFamily,
    int pageWidth, boolean unicode) {
    return new StringPrinterConfig(new UnixTermStylist(colorScheme, styleFamily), pageWidth, unicode);
  }

  public static @NotNull StringPrinterConfig unixTerminal(@NotNull StyleFamily styleFamily, int pageWidth, boolean unicode) {
    return new StringPrinterConfig(new UnixTermStylist(AyaColorScheme.EMACS, styleFamily), pageWidth, unicode);
  }

  public static @NotNull StringPrinterConfig unixTerminal(int pageWidth) {
    return unixTerminal(AyaStyleFamily.DEFAULT, pageWidth, true);
  }

  public static @NotNull StringPrinterConfig unixTerminal() {
    return unixTerminal(INFINITE_SIZE);
  }
}
