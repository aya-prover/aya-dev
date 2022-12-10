// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.string;

import org.aya.pretty.printer.PrinterConfig;
import org.jetbrains.annotations.NotNull;

public class StringPrinterConfig<S extends StringStylist> extends PrinterConfig.Basic<S> {
  public final boolean unicode;

  public StringPrinterConfig(@NotNull S stylist, int pageWidth, boolean unicode) {
    super(pageWidth, INFINITE_SIZE, stylist);
    this.unicode = unicode;
  }
}
