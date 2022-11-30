// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableSeq;
import kala.tuple.Tuple2;
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

  /**
   * all line separators are treat as 1 character long
   *
   * @return a (line, index of the first character) list
   */
  static @NotNull ImmutableSeq<Tuple2<String, Integer>> indexedLines(@NotNull String str) {
    var lines = ImmutableSeq.from(str.lines());
    var indexes = MutableList.<Integer>create();

    var lastLineIndex = -1;
    var lastLineLength = -1;

    for (var line : lines) {
      var index = lastLineIndex == -1
        ? 0
        : lastLineIndex + lastLineLength + 1;

      indexes.append(index);
      lastLineIndex = index;
      lastLineLength = line.length();
    }

    return lines.zip(indexes);
  }
}
