// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.data;

import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;

public interface FieldRef {
  record Default(
    @NotNull ClassDesc owner,
    @NotNull ClassDesc returnType,
    @NotNull String name
  ) implements FieldRef {
  }

  @NotNull ClassDesc owner();
  @NotNull ClassDesc returnType();
  @NotNull String name();
}
