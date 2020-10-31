// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.api.util;

/**
 * @author re-xyr
 */
public enum DTKind {
  Pi, Copi,
  Sigma, Cosigma;

  public boolean isPi() {
    return this == Pi || this == Copi;
  }

  public boolean isSigma() {
    return this == Sigma || this == Cosigma;
  }
}
