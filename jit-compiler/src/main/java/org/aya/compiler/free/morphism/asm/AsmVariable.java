// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism.asm;

import java.lang.constant.ClassDesc;

import org.aya.compiler.free.data.LocalVariable;
import org.glavo.classfile.TypeKind;
import org.jetbrains.annotations.NotNull;

public record AsmVariable(int slot, @NotNull ClassDesc type) implements LocalVariable {
  public @NotNull TypeKind kind() {
    return TypeKind.fromDescriptor(type.descriptorString());
  }

  @Override public @NotNull AsmExpr ref() {
    return AsmExpr.withType(type, builder -> builder.writer().loadInstruction(kind(), slot));
  }
}
