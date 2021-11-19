// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic.ref;

import org.aya.api.ref.Var;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @param sourcePos null if this is a generalized level variable,
 *                  otherwise it denotes a to-be-solved level.
 * @author ice1000
 * @apiNote used only in concrete syntax.
 */
public record PreLevelVar(@NotNull String name, @Nullable SourcePos sourcePos) implements Var {
  public PreLevelVar(@NotNull String name) {
    this(name, null);
  }

  @Override public boolean equals(@Nullable Object o) {
    return this == o;
  }

  @Override public int hashCode() {
    return System.identityHashCode(this);
  }
}
