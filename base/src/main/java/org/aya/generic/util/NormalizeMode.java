// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic.util;

/**
 * @author kiva
 */
public enum NormalizeMode {
  /**
   * Do NOT normalize.
   */
  NULL,
  /**
   * Fully normalize.
   */
  NF,
  /**
   * Normalize until the head is canonical.
   */
  WHNF,
}
