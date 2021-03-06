// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete;

import org.aya.api.error.SourcePos;
import org.aya.util.Constants;
import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

public record QualifiedID(
  @NotNull SourcePos sourcePos,
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
    return ids.joinToString(Constants.SCOPE_SEPARATOR);
  }
}
