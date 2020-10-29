// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.generic;

import org.mzi.core.term.PiTerm;

/**
 * What kind of (co)dependent type ({@link PiTerm}) is it?
 *
 * @author ice1000
 */
public enum DTKind {
  Pi(true, true),
  Sigma(false, true),
  Copi(true, false),
  Cosigma(false, false);

  /**
   * This value is:
   * <ul>
   *   <li>true if this is a function-like (co)dependent type</li>
   *   <li>false if this is a tuple-like (co)dependent type</li>
   * </ul>
   */
  public final boolean function;
  /**
   * This value is:
   * <ul>
   *   <li>true if this is a dependent type (say, latter member depends on prior ones)</li>
   *   <li>false if this is a codependent type (former depends on latter)</li>
   * </ul>
   */
  public final boolean forward;

  DTKind(boolean function, boolean forward) {
    this.function = function;
    this.forward = forward;
  }
}
