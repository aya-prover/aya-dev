// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.ref;

import org.aya.syntax.concrete.stmt.Generalize;
import org.aya.util.position.SourceNode;
import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.NotNull;

public final class GeneralizedVar implements AnyVar, SourceNode {
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

  public @NotNull String name() { return name; }
  @Override public @NotNull SourcePos sourcePos() { return sourcePos; }
}
