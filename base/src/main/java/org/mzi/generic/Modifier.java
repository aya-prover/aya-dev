// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.generic;

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
   * Denotes that a a function's invocations are eagerly reduced.
   */
  Inline,
}
