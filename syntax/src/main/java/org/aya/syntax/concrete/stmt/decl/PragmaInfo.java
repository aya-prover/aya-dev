// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete.stmt.decl;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.Suppress;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PragmaInfo {
  /// @param sourcePos the name, not the whole pragma
  public record SuppressWarn(@NotNull SourcePos sourcePos, @NotNull ImmutableSeq<WithPos<Suppress>> args) {
  }

  public @Nullable SuppressWarn suppressWarn = null;

  public PragmaInfo() {
  }
}
