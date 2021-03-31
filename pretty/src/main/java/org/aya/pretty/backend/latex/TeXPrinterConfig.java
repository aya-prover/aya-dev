// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.backend.latex;

import org.aya.pretty.backend.string.StringPrinterConfig;
import org.aya.pretty.backend.string.StringStylist;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public class TeXPrinterConfig extends StringPrinterConfig {
  public TeXPrinterConfig(@NotNull StringStylist formatter, int pageWidth) {
    super(formatter, pageWidth);
  }
}
