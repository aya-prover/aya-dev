// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ast;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public interface AstArgsProvider {
  @Contract(pure = true)
  @NotNull AstVariable arg(int nth);

  record FnParam(int paramCount) implements AstArgsProvider {
    @Override public @NotNull AstVariable arg(int nth) {
      assert nth < paramCount;
      return new AstVariable.Arg(nth);
    }
  }

  record Lambda(int captureCount, int paramCount) implements AstArgsProvider {
    @Contract(pure = true)
    public @NotNull AstVariable capture(int nth) {
      assert nth < captureCount;
      return new AstVariable.Capture(nth);
    }

    @Override public @NotNull AstVariable arg(int nth) {
      assert nth < paramCount;
      return new AstVariable.Arg(nth);
    }
  }
}
