// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.generic;

/**
 * @author kiva
 */
public enum Modifier {
  /**
   * Denotes that a function's invocations are never reduced,
   * and should be removed during elaboration.
   */
  Erase,
  /**
   * Denotes that a function's invocations are eagerly reduced.
   */
  Inline,
}
