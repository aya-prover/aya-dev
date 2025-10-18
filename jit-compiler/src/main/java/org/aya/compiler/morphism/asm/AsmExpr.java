// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.asm;

import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.util.function.Consumer;

/// An expr of bytecode is a function that push something to the stack
public record AsmExpr(
  @NotNull ClassDesc type,
  @NotNull Consumer<AsmCodeBuilder> cont
) implements Consumer<AsmCodeBuilder> {
  public static @NotNull AsmExpr withType(@NotNull ClassDesc type, @NotNull Consumer<AsmCodeBuilder> cont) {
    return new AsmExpr(type, cont);
  }
  @Override public void accept(AsmCodeBuilder builder) { cont.accept(builder); }
}
