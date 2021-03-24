// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.backend.string;

import org.aya.pretty.printer.PrinterConfig;

public class StringPrinterConfig extends PrinterConfig.Basic {
  public StringPrinterConfig() {
    this(PrinterConfig.INFINITE_SIZE);
  }

  public StringPrinterConfig(int pageWidth) {
    super(pageWidth, PrinterConfig.INFINITE_SIZE);
  }
}
