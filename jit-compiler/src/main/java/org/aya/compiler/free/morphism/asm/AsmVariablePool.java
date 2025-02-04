// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism.asm;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;

public record AsmVariablePool(int offset, @NotNull MutableList<AsmVariable> vars) {
  public static @NotNull AsmVariablePool from(@Nullable ClassDesc thisType, @NotNull ImmutableSeq<ClassDesc> telescope) {
    var scope = new AsmVariablePool();
    if (thisType != null) {
      scope.acquire(thisType);
    }

    telescope.forEach(scope::acquire);

    return scope;
  }

  public AsmVariablePool() {
    this(0, MutableList.create());
  }

  public @NotNull AsmVariable acquire(@NotNull ClassDesc varType) {
    var slot = offset + vars.size();
    var var = new AsmVariable(slot, varType);
    vars.append(var);
    return var;
  }

  public int length() {
    return offset + vars.size();
  }

  public @NotNull AsmVariablePool subscope() {
    return new AsmVariablePool(length(), MutableList.create());
  }

  public void submit(@NotNull AsmCodeBuilder builder) {
    vars.forEach(var -> builder.writer().localVariable(
      var.slot(),
      "var" + var.slot(),
      var.type(),
      builder.writer().startLabel(),
      builder.writer().endLabel()
    ));
  }
}
