// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface HighlightInfoType {
  enum DefKind {
    Generalized, Data, Con, Struct, Field, Fn, Prim, Local
  }

  enum LitKind {
    Int, String, Keyword, Sort
  }

  /**
   * @param target the reference target, a unique string to the definition (for now, it is {@link Object#hashCode()})
   * @param kind   null if not sure
   */
  record SymRef(@NotNull String target, @Nullable DefKind kind) implements HighlightInfoType {
  }

  /**
   * similar to {@link SymRef}, but this is a definition
   */
  record SymDef(@NotNull String target, @Nullable DefKind kind) implements HighlightInfoType {
  }

  record SymError(@Nullable Doc description) implements HighlightInfoType {
  }

  record Lit(@NotNull LitKind kind) implements HighlightInfoType {
  }
}
