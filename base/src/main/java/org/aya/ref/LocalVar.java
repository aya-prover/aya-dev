// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ref;

import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * @author ice1000
 */
public record LocalVar(@NotNull String name, @NotNull SourcePos definition) implements Var {

  @Contract(" -> new")
  public static @NotNull LocalVar ignoredLocal() {
    return new LocalVar("AYA_INTERNAL_IGNORED_LOCALVAR" + UUID.randomUUID());
  }

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
