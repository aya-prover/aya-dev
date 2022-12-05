// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import org.aya.ref.AnyVar;
import org.aya.ref.GenerateKind;
import org.aya.ref.LocalVar;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public final class GeneralizedVar implements AnyVar {
  public final @NotNull String name;
  public final @NotNull SourcePos sourcePos;
  public Generalize owner;

  public GeneralizedVar(@NotNull String name, @NotNull SourcePos sourcePos) {
    this.name = name;
    this.sourcePos = sourcePos;
  }

  public @NotNull LocalVar toLocal() {
    return new LocalVar(name, sourcePos, new GenerateKind.Generalized(this));
  }

  public @NotNull String name() {
    return name;
  }
}
