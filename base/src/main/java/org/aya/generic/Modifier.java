// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
public enum Modifier {
  /**
   * Denotes that a function's invocations are never reduced,
   * and should be removed during elaboration.
   */
  Erase("erase"),
  /**
   * Denotes that a function's invocations are eagerly reduced.
   */
  Inline("inline"),
  Pattern("pattern"),
  ;

  public final @NotNull String keyword;

  Modifier(@NotNull String keyword) {
    this.keyword = keyword;
  }
}
