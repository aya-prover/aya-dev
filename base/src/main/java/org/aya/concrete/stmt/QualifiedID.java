// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.Constants;
import org.aya.resolve.context.ModulePath;
import org.aya.util.error.SourceNode;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public record QualifiedID(
  @Override @NotNull SourcePos sourcePos,
  @NotNull ModulePath component,
  @NotNull String name
) implements SourceNode {
  /**
   * @param ids not empty
   */
  public QualifiedID(@NotNull SourcePos sourcePos, @NotNull ImmutableSeq<String> ids) {
    this(sourcePos, ModulePath.from(ids.dropLast(1)), ids.last());
  }

  public QualifiedID(@NotNull SourcePos sourcePos, @NotNull String id) {
    this(sourcePos, ImmutableSeq.of(id));
  }

  public @NotNull ImmutableSeq<String> ids() {
    return component().toImmutableSeq().appended(name);
  }

  public boolean isUnqualified() {
    return component() == ModulePath.This;
  }

  public @NotNull String join() {
    return join(ids());
  }

  public @NotNull ModulePath.Qualified asModulePath() {
    return component().resolve(name);
  }

  public static @NotNull String join(@NotNull Seq<@NotNull String> ids) {
    return ids.joinToString(Constants.SCOPE_SEPARATOR);
  }
}
