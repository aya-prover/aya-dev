// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.more;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.tuple.primitive.IntObjTuple2;
import org.jetbrains.annotations.NotNull;

public interface StringUtil {
  /** <a href="https://github.com/JetBrains/Arend/blob/39b14869ac5abdcee7bbee0efa06d5a6f86c4069/cli/src/main/java/org/arend/frontend/library/TimedLibraryManager.java#L21-L31">Arend</a> */
  static @NotNull String timeToString(long time) {
    if (time < 10000) return time + "ms";
    if (time < 60000) return time / 1000 + ("." + (time / 100 % 10)) + "s";
    var seconds = time / 1000;
    return (seconds / 60) + "m" + (seconds % 60) + "s";
  }

  /**
   * all line separators are treat as 1 character long
   *
   * @return a (index of the first character, line) list
   */
  static @NotNull ImmutableSeq<IntObjTuple2<String>> indexedLines(@NotNull String str) {
    var lines = ImmutableSeq.from(str.lines());
    var results = MutableList.<IntObjTuple2<String>>create();

    var index = 0;
    for (var line : lines) {
      results.append(IntObjTuple2.of(index, line));
      index = index + line.length() + 1;    // 1 for the line separator
    }

    return results.toImmutableSeq();
  }
}
