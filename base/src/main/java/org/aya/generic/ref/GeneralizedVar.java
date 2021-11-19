// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic.ref;

import org.aya.api.ref.Var;
import org.aya.concrete.stmt.Generalize;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public final class GeneralizedVar implements Var {
  public final @NotNull String name;
  public final @NotNull SourcePos sourcePos;
  public Generalize.Variables owner;

  public GeneralizedVar(@NotNull String name, @NotNull SourcePos sourcePos) {
    this.name = name;
    this.sourcePos = sourcePos;
  }

  public @NotNull String name() {
    return name;
  }
}
