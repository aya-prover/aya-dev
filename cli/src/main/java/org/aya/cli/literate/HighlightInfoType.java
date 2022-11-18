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

  final class Keyword implements HighlightInfoType {
    public final static Keyword INSTANCE = new Keyword();

    private Keyword() {
    }
  }

  /**
   * @param target the reference target, a unique string to the definition (for now, it is {@link Object#hashCode()})
   * @param kind null if not sure
   */
  record Ref(@NotNull String target, @Nullable HighlightInfoType.DefKind kind) implements HighlightInfoType {
  }

  /**
   * similar to {@link HighlightInfoType.Ref}, but this is a definition
   */
  record Def(@NotNull String target, @Nullable HighlightInfoType.DefKind kind) implements HighlightInfoType {
  }

  record Error(@Nullable Doc description) implements HighlightInfoType {
  }

  final class LitInt implements HighlightInfoType {
    public final static LitInt INSTANCE = new LitInt();

    private LitInt() {
    }
  }

  final class LitString implements HighlightInfoType {
    public final static LitString INSTANCE = new LitString();

    private LitString() {
    }
  }
}
