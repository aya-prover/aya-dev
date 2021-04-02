// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.backend.latex;

import org.aya.pretty.backend.string.StringPrinterConfig;

/**
 * @author ice1000
 */
public class TeXPrinterConfig extends StringPrinterConfig {
  public TeXPrinterConfig() {
    super(new TeXStylist(), INFINITE_SIZE);
  }
}
