// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.prettier;


import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public abstract class PrettierOptions {
  public final Map<Key, Boolean> map;

  @SuppressWarnings({"rawtypes", "unchecked"})
  public PrettierOptions(@NotNull Class<?> keyClass) {
    if (keyClass.isEnum()) map = new EnumMap(keyClass);
    else map = new HashMap<>();
    reset();
  }

  public abstract void reset();
  public interface Key { }
}
