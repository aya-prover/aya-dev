// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.ref;

import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record LocalVar(
  @NotNull String name,
  @NotNull SourcePos definition,
  @NotNull GenerateKind generateKind
) implements AnyVar {
  public LocalVar(@NotNull String name) {
    this(name, SourcePos.NONE);
  }

  public LocalVar(@NotNull String name, @NotNull SourcePos definition) {
    this(name, definition, GenerateKind.Basic.None);
  }

  public static @NotNull LocalVar generate(@NotNull String name, @NotNull SourcePos sourcePos) {
    return new LocalVar(name, sourcePos, GenerateKind.Basic.Tyck);
  }

  public static @NotNull LocalVar generate(@NotNull String name) {
    return generate(name, SourcePos.NONE);
  }

  public static @NotNull LocalVar from(@NotNull WithPos<String> id) {
    return make(id.data(), id.sourcePos());
  }

  public static @NotNull LocalVar make(@Nullable String name, @NotNull SourcePos sourcePos) {
    if (name == null) return new LocalVar("_", sourcePos);
    return new LocalVar(name, sourcePos);
  }

  public static final @NotNull LocalVar IGNORED = new LocalVar("_", SourcePos.NONE);
  @Override public boolean equals(@Nullable Object o) { return this == o; }
  @Override public int hashCode() { return System.identityHashCode(this); }

  public boolean isGenerated() {
    return generateKind != GenerateKind.Basic.None;
  }
}
