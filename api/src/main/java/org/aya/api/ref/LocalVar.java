// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.api.ref;

import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000
 */
public record LocalVar(@NotNull String name, @NotNull SourcePos definition) implements Var {
  public LocalVar(@NotNull String name) {
    this(name, SourcePos.NONE);
  }

  public static @NotNull LocalVar from(@NotNull WithPos<String> name) {
    return new LocalVar(name.data(), name.sourcePos());
  }

  @Override public boolean equals(@Nullable Object o) {
    return this == o;
  }

  @Override public int hashCode() {
    return System.identityHashCode(this);
  }
}
