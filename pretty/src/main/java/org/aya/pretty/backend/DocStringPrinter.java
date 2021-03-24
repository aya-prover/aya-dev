// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.backend;

import org.aya.pretty.backend.string.StringPrinterConfig;
import org.aya.pretty.backend.string.StringPrinter;
import org.aya.pretty.backend.string.StringRenderer;

public class DocStringPrinter extends StringPrinter<StringPrinterConfig> {
  public DocStringPrinter() {
    super(new StringRenderer<>());
  }
}
