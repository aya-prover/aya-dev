// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.ser;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.concrete.stmt.UseHide;
import org.jetbrains.annotations.NotNull;

/// @see UseHide
public record SerUseHide(
  boolean isUsing,
  @NotNull ImmutableSeq<SerQualifiedID> names,
  @NotNull ImmutableSeq<SerRename> renames
) {
  public static @NotNull SerUseHide from(@NotNull UseHide useHide) {
    return new SerUseHide(
      useHide.strategy() == UseHide.Strategy.Using,
      useHide.list().map(x -> SerQualifiedID.from(x.id())),
      useHide.renaming().map(it -> SerRename.from(it.data()))
    );
  }
}
