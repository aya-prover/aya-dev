// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.pretty.error;

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

  class Default implements PrettyErrorConfig {
  }
}
