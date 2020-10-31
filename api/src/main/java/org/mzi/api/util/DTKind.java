// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.api.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

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
