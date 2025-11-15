// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.asm;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public sealed interface AsmValue extends Consumer<AsmCodeBuilder> {
  record AsmValuriable(@NotNull AsmVariable variable) implements AsmValue {
    @Override public void accept(AsmCodeBuilder builder) {
      builder.loadVar(variable);
    }
  }

  record AsmExprValue(@NotNull AsmExpr expr) implements AsmValue {
    @Override public void accept(AsmCodeBuilder builder) {
      expr.accept(builder);
    }
  }
}
