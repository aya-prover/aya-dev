// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.misc;

import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableList;
import kala.control.Option;
import org.aya.util.Pair;

public class NestedEnvLookup<T> {
  private final MutableHashMap<String, MutableList<T>> binds = MutableHashMap.create();
  private final MutableList<String> order = MutableList.create();

  public void add(String key, T value) {
    order.append(key);
    binds.putIfAbsent(key, MutableList.create());
    binds.get(key).append(value);
  }

  public Pair<String, T> pop() {
    var lastKey = order.removeLast();
    var lastVal = binds.get(lastKey).removeLast();
    return new Pair<>(lastKey, lastVal);
  }

  public Option<T> lookup(String key) {
    var entries = binds.getOption(key);
    return entries.flatMap(MutableList::getLastOption);
  }
}
