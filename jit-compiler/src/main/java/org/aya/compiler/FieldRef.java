// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;

public record FieldRef(
  @NotNull ClassDesc owner,
  @NotNull ClassDesc returnType,
  @NotNull String name
) {
}
