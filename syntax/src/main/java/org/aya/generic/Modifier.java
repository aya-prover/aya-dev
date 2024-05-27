// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
public enum Modifier {
  /**
   * Denotes that a function's invocations are never reduced.
   * Useful in debugging, when you really don't wanna see the full NF.
   */
  Opaque("opaque"),
  /**
   * Denotes that a function's invocations are eagerly reduced.
   */
  Inline("inline"),
  /**
   * That this function uses overlapping and order-insensitive
   * pattern matching semantics.
   */
  Overlap("overlap"),
  ;

  public final @NotNull String keyword;

  Modifier(@NotNull String keyword) {
    this.keyword = keyword;
  }
}
