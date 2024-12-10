// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism;

import org.aya.compiler.free.FreeJava;
import org.aya.compiler.free.data.LocalVariable;
import org.jetbrains.annotations.NotNull;

public record SourceFreeJava(@NotNull String expr) implements FreeJava, LocalVariable {
  @Override
  public @NotNull FreeJava ref() {
    return this;
  }
}
