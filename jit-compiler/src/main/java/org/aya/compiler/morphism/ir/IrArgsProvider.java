// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ir;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public interface IrArgsProvider {
  @Contract(pure = true)
  @NotNull IrVariable arg(int nth);

  record FnParam(int paramCount) implements IrArgsProvider {
    @Override public @NotNull IrVariable arg(int nth) {
      assert nth < paramCount;
      return new IrVariable.Arg(nth);
    }
  }

  record Lambda(int captureCount, int paramCount) implements IrArgsProvider {
    @Contract(pure = true)
    public @NotNull IrVariable capture(int nth) {
      assert nth < captureCount;
      return new IrVariable.Capture(nth);
    }

    @Override public @NotNull IrVariable arg(int nth) {
      assert nth < paramCount;
      return new IrVariable.Arg(nth);
    }
  }
}
