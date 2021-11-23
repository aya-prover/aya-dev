// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

import org.jetbrains.annotations.NotNull;

public interface StringUtil {
  /** https://github.com/JetBrains/Arend/blob/39b14869ac5abdcee7bbee0efa06d5a6f86c4069/cli/src/main/java/org/arend/frontend/library/TimedLibraryManager.java#L21-L3 */
  static @NotNull String timeToString(long time) {
    if (time < 10000) return time + "ms";
    if (time < 60000) return time / 1000 + ("." + (time / 100 % 10)) + "s";
    var seconds = time / 1000;
    return (seconds / 60) + "m" + (seconds % 60) + "s";
  }

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
