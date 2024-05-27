// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete.stmt;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.Constants;
import org.aya.util.error.SourceNode;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public record QualifiedID(
  @Override @NotNull SourcePos sourcePos,
  @NotNull ModuleName component,
  @NotNull String name
) implements SourceNode {
  /**
   * @param ids not empty
   */
  public QualifiedID(@NotNull SourcePos sourcePos, @NotNull ImmutableSeq<String> ids) {
    this(sourcePos, ModuleName.from(ids.dropLast(1)), ids.getLast());
  }

  public QualifiedID(@NotNull SourcePos sourcePos, @NotNull String id) {
    this(sourcePos, ImmutableSeq.of(id));
  }

  public @NotNull ImmutableSeq<String> ids() {
    return component().ids().appended(name);
  }

  public boolean isUnqualified() { return component() == ModuleName.This; }
  public @NotNull String join() { return join(ids()); }
  public static @NotNull String join(@NotNull Seq<@NotNull String> ids) {
    return ids.joinToString(Constants.SCOPE_SEPARATOR);
  }
}
