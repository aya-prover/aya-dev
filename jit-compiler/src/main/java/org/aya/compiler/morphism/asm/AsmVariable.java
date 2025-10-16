// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.asm;

import org.aya.compiler.LocalVariable;
import org.glavo.classfile.TypeKind;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;

public record AsmVariable(int slot, @NotNull ClassDesc type, boolean isThis) implements LocalVariable {
  public AsmVariable {
    assert !isThis || slot == 0;
  }

  public @NotNull TypeKind kind() {
    return TypeKind.fromDescriptor(type.descriptorString());
  }

  public @NotNull AsmExpr ref() {
    return AsmExpr.withType(type, builder -> builder.writer().loadInstruction(kind(), slot));
  }

  public static @NotNull AsmVariable mkThis(@NotNull ClassDesc type) {
    return new AsmVariable(0, type, true);
  }

  public @NotNull String name() {
    return isThis ? "this" : ("var" + slot);
  }
}
