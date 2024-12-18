// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism.free;

import org.aya.compiler.free.ArgumentProvider;
import org.aya.compiler.free.FreeJavaExpr;
import org.aya.compiler.free.data.LocalVariable;
import org.jetbrains.annotations.NotNull;

public record FreeArgumentProvider(int paramCount) implements ArgumentProvider {
  @Override
  public @NotNull LocalVariable arg(int nth) {
    assert nth < paramCount;
    return new FreeVariable(nth);
  }

  record Lambda(int captureCount, int paramCount) implements ArgumentProvider.Lambda {
    @Override
    public @NotNull FreeJavaExpr capture(int nth) {
      assert nth < captureCount;
      return new FreeExpr.RefCapture(nth);
    }

    @Override
    public @NotNull LocalVariable arg(int nth) {
      assert nth + captureCount < paramCount;
      return new FreeVariable(nth);
    }
  }
}
