// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.prettier;

import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class AyaPrettierOptions extends PrettierOptions {
  public AyaPrettierOptions() {
    super(Key.class);
  }

  public enum Key implements PrettierOptions.Key {
    InlineMetas,
    ShowImplicitArgs,
    ShowImplicitPats,
    ShowLambdaTypes,
  }

  @Override public void reset() {
    for (Key value : Key.values()) map.put(value, false);
    map.put(Key.InlineMetas, true);
  }

  @Contract(pure = true, value = "->new") public static @NotNull AyaPrettierOptions debug() {
    var map = informative();
    map.map.put(Key.ShowLambdaTypes, true);
    return map;
  }

  @Contract(pure = true, value = "->new") public static @NotNull AyaPrettierOptions informative() {
    var map = pretty();
    map.map.put(Key.ShowImplicitArgs, true);
    return map;
  }

  @Contract(pure = true, value = "->new") public static @NotNull AyaPrettierOptions pretty() {
    var map = new AyaPrettierOptions();
    map.map.put(Key.ShowImplicitPats, true);
    return map;
  }
}
