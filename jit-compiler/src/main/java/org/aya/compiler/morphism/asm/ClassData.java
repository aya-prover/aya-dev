// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.asm;

import org.glavo.classfile.AccessFlag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;

public record ClassData(
  @NotNull ClassDesc className,
  @NotNull ClassDesc classSuper,
  @Nullable Outer outer
) {
  public static final int AF_NESTED =
    AccessFlag.PUBLIC.mask() | AccessFlag.FINAL.mask() | AccessFlag.STATIC.mask();

  record Outer(@NotNull ClassData data, @NotNull String thisName) { }
}
