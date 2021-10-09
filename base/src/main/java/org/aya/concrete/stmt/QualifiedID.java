// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import org.aya.api.error.SourcePos;
import org.aya.util.Constants;
import org.jetbrains.annotations.NotNull;

public record QualifiedID(
  @Override @NotNull SourcePos sourcePos,
  @NotNull ImmutableSeq<@NotNull String> ids
) {
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
