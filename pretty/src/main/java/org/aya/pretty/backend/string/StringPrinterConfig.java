// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.string;

import org.aya.pretty.backend.string.style.UnixTermStylist;
import org.aya.pretty.printer.PrinterConfig;
import org.aya.pretty.printer.StyleFamily;
import org.aya.pretty.style.AyaStyleFamily;
import org.jetbrains.annotations.NotNull;

public class StringPrinterConfig extends PrinterConfig.Basic {
  public final @NotNull StringStylist stylist;
  public final boolean unicode;

  public StringPrinterConfig(@NotNull StringStylist stylist, int pageWidth, boolean unicode) {
    super(pageWidth, INFINITE_SIZE);
    this.stylist = stylist;
    this.unicode = unicode;
  }

  @Override
  public @NotNull StringStylist getStylist() {
    return stylist;
  }

  public static @NotNull StringPrinterConfig unixTerminal(@NotNull StyleFamily styleFamily, int pageWidth, boolean unicode) {
    return new StringPrinterConfig(new UnixTermStylist(styleFamily), pageWidth, unicode);
  }

  public static @NotNull StringPrinterConfig unixTerminal(int pageWidth) {
    return unixTerminal(AyaStyleFamily.DEFAULT, pageWidth, true);
  }

  public static @NotNull StringPrinterConfig unixTerminal() {
    return unixTerminal(INFINITE_SIZE);
  }
}
