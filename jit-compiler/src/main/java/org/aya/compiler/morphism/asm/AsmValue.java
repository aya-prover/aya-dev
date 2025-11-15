// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.asm;

import org.jetbrains.annotations.NotNull;

import java.lang.invoke.TypeDescriptor;
import java.util.function.Consumer;

/// This is not much different from [AsmExpr], but we use this as a hint that
/// the value represented by it is relatively simple (like a variable or a constant).
public sealed interface AsmValue extends Consumer<AsmCodeBuilder> {
  @NotNull TypeDescriptor type();
  record AsmValuriable(@NotNull AsmVariable variable) implements AsmValue {
    @Override public void accept(AsmCodeBuilder builder) { builder.loadVar(variable); }
    @Override public @NotNull TypeDescriptor type() { return variable.type(); }
  }

  record AsmExprValue(@NotNull AsmExpr expr) implements AsmValue {
    @Override public void accept(AsmCodeBuilder builder) { expr.accept(builder); }
    @Override public @NotNull TypeDescriptor type() { return expr.type(); }
  }
}
