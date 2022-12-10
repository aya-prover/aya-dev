// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.distill;

import org.aya.util.distill.DistillerOptions;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class AyaDistillerOptions extends DistillerOptions {
  public enum Key implements DistillerOptions.Key {
    InlineMetas,
    ShowImplicitArgs,
    ShowImplicitPats,
    ShowLambdaTypes,
  }

  @Override public void reset() {
    for (Key value : Key.values()) map.put(value, false);
    map.put(Key.InlineMetas, true);
  }

  @Contract(pure = true, value = "->new") public static @NotNull AyaDistillerOptions debug() {
    var map = informative();
    map.map.put(Key.ShowLambdaTypes, true);
    return map;
  }

  @Contract(pure = true, value = "->new") public static @NotNull AyaDistillerOptions informative() {
    var map = pretty();
    map.map.put(Key.ShowImplicitArgs, true);
    return map;
  }

  @Contract(pure = true, value = "->new") public static @NotNull AyaDistillerOptions pretty() {
    var map = new AyaDistillerOptions();
    map.map.put(Key.ShowImplicitPats, true);
    return map;
  }
}
