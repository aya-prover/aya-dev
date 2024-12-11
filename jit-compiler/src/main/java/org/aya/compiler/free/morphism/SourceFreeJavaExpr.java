// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism;

import org.aya.compiler.free.FreeJavaExpr;
import org.aya.compiler.free.data.LocalVariable;
import org.jetbrains.annotations.NotNull;

public record SourceFreeJavaExpr(@NotNull String expr) implements FreeJavaExpr, LocalVariable {
  @Override
  public @NotNull FreeJavaExpr ref() {
    return this;
  }
}
