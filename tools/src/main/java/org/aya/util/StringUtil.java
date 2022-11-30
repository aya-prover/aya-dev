// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
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

  static @NotNull String trimCRLF(@NotNull String string) {
    return string.replaceAll("\\r\\n?", "\n");
  }
}
