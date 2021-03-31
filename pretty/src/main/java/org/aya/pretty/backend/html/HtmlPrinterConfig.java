// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.backend.html;

import org.aya.pretty.backend.string.StringPrinterConfig;

public class HtmlPrinterConfig extends StringPrinterConfig {
  public HtmlPrinterConfig() {
    super(new Html5Stylist(), INFINITE_SIZE);
  }
}
