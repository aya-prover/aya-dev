// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.util;

/**
 * @author re-xyr
 */
public enum DTKind {
  Pi(true, false), Copi(true, true),
  Sigma(false, false), Cosigma(false, true);

  public final boolean isPi;
  public final boolean isSigma;
  public final boolean co;

  DTKind(boolean isPi, boolean co) {
    this.isPi = isPi;
    this.isSigma = !isPi;
    this.co = co;
  }
}
