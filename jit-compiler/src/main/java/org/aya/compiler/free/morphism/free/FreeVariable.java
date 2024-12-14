// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism.free;

import org.aya.compiler.free.FreeJavaExpr;
import org.aya.compiler.free.data.LocalVariable;
import org.jetbrains.annotations.NotNull;

public record FreeVariable(int index) implements LocalVariable {
  @Override
  public @NotNull FreeJavaExpr ref() {
    return new FreeExpr.RefVariable(this);
  }
}
