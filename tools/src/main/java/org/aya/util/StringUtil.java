// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

import org.jetbrains.annotations.NotNull;

public interface StringUtil {
  /** https://stackoverflow.com/a/25379180/7083401 */
  static boolean containsIgnoreCase(@NotNull String src, @NotNull String what) {
    var length = what.length();
    if (length == 0) return true; // Empty string is contained

    var firstLo = Character.toLowerCase(what.charAt(0));
    var firstUp = Character.toUpperCase(what.charAt(0));

    for (int i = src.length() - length; i >= 0; i--) {
      // Quick check before calling the more expensive regionMatches() method:
      var ch = src.charAt(i);
      if (ch != firstLo && ch != firstUp) continue;
      if (src.regionMatches(true, i, what, 0, length)) return true;
    }

    return false;
  }
}
