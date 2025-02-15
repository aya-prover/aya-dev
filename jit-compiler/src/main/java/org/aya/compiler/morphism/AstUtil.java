// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism;

import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;

public final class AstUtil {
  private AstUtil() { }

  public static @NotNull ClassDesc fromClass(@NotNull Class<?> clazz) {
    return ClassDesc.ofDescriptor(clazz.descriptorString());
  }
}
