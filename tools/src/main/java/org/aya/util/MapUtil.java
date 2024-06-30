// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

import kala.collection.immutable.ImmutableMap;
import org.jetbrains.annotations.NotNull;

public interface MapUtil {
  static <K, V> boolean sameElements(@NotNull ImmutableMap<K, V> lhs, @NotNull ImmutableMap<K, V> rhs) {
    if (lhs.size() != rhs.size()) return false;

    var it = lhs.iterator();
    while (it.hasNext()) {
      var pair = it.next();
      if (!rhs.containsKey(pair.component1())) return false;
      if (pair.component2() != rhs.get(pair.component1())) return false;
    }

    return true;
  }
}
