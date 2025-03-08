// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.ref;

import org.aya.generic.Constants;
import org.aya.util.position.SourceNode;
import org.aya.util.position.SourcePos;
import org.aya.util.position.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record LocalVar(
  @NotNull String name,
  @NotNull SourcePos definition,
  @NotNull GenerateKind generateKind
) implements AnyVar, SourceNode {
  public LocalVar(@NotNull String name) {
    this(name, SourcePos.NONE);
  }

  public LocalVar(@NotNull String name, @NotNull SourcePos definition) {
    this(name, definition, GenerateKind.Basic.None);
  }

  public static @NotNull LocalVar generate(@NotNull SourcePos sourcePos) {
    return generate(Constants.ANONYMOUS_PREFIX, sourcePos);
  }

  public static @NotNull LocalVar generate(@NotNull String name, @NotNull SourcePos sourcePos) {
    return new LocalVar(name, sourcePos, GenerateKind.Basic.Tyck);
  }

  public static @NotNull LocalVar generate(@NotNull String name) {
    return generate(name, SourcePos.NONE);
  }

  public static @NotNull LocalVar from(@NotNull WithPos<String> id) {
    if (id.data() == null) return new LocalVar("_", id.sourcePos());
    return new LocalVar(id.data(), id.sourcePos());
  }

  public static final @NotNull LocalVar IGNORED = new LocalVar("_", SourcePos.NONE, GenerateKind.Basic.Anonymous);
  @Override public @NotNull SourcePos sourcePos() { return definition; }
  @Override public boolean equals(@Nullable Object o) { return this == o; }
  @Override public int hashCode() { return System.identityHashCode(this); }

  public boolean isGenerated() {
    return generateKind != GenerateKind.Basic.None;
  }
}
