// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.Constants;
import org.aya.util.binop.SourceNode;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public record QualifiedID(
  @Override @NotNull SourcePos sourcePos,
  @NotNull ImmutableSeq<@NotNull String> ids
) implements SourceNode {
  public QualifiedID(@NotNull SourcePos sourcePos, @NotNull String id) {
    this(sourcePos, ImmutableSeq.of(id));
  }

  public boolean isUnqualified() {
    return ids.sizeEquals(1);
  }

  public boolean isQualified() {
    return ids.sizeGreaterThan(1);
  }

  public @NotNull String justName() {
    return ids.last();
  }

  public @NotNull String join() {
    return join(this.ids);
  }

  public static @NotNull String join(@NotNull Seq<@NotNull String> ids) {
    return ids.joinToString(Constants.SCOPE_SEPARATOR);
  }
}
