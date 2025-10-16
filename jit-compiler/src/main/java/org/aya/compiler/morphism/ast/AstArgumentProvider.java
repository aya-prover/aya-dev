// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ast;

import org.aya.compiler.LocalVariable;
import org.aya.compiler.morphism.ArgumentProvider;
import org.jetbrains.annotations.NotNull;

public record AstArgumentProvider(int paramCount) implements ArgumentProvider {
  @Override public @NotNull LocalVariable arg(int nth) {
    assert nth < paramCount;
    return new AstVariable.Arg(nth);
  }

  record Lambda(int captureCount, int paramCount) implements ArgumentProvider.Lambda {
    @Override public @NotNull AstExpr capture(int nth) {
      assert nth < captureCount;
      return new AstExpr.RefCapture(nth);
    }

    @Override public @NotNull LocalVariable arg(int nth) {
      assert nth < paramCount;
      return new AstVariable.Arg(nth);
    }
  }
}
