// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.error;

import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
public interface PrettyErrorConfig {
  /**
   * Returns the number of spaces occupied by the tab in different terminals.
   * e.g. return 4 for normal console output, 2 for compact format style.
   * @return space count
   */
  default int tabWidth() {
    return 4;
  }

  /**
   * Show more lines before startLine and after endLine
   * @return line count
   */
  default int showMore() {
    return 2;
  }

  @NotNull PrettyErrorConfig DEFAULT = new PrettyErrorConfig() {
  };
}
