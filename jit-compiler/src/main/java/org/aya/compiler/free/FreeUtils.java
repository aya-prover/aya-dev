// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free;

import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;

public final class FreeUtils {
  private FreeUtils() { }

  public static @NotNull ClassDesc fromClass(@NotNull Class<?> clazz) {
    return ClassDesc.ofDescriptor(clazz.descriptorString());
  }
}
