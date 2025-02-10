// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.extra;

import kala.collection.immutable.primitive.ImmutableIntArray;
import kala.collection.mutable.primitive.MutableIntList;
import kala.value.primitive.MutableIntValue;
import org.jetbrains.annotations.NotNull;

public interface ParsingUtil {
  /**
   * all line separators are treat as 1 character long
   *
   * @return a sequence of integers denoting the start position of each line
   */
  static @NotNull ImmutableIntArray indexedLines(@NotNull String str) {
    var results = MutableIntList.create();
    var index = MutableIntValue.create();
    str.lines().forEach(line -> {
      results.append(index.get());
      index.add(line.length() + 1);
    });
    return results.toImmutableArray();
  }
}
