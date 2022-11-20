// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import org.aya.generic.AyaDocile;
import org.aya.pretty.backend.string.LinkId;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public record HighlightInfo(
  @NotNull SourcePos sourcePos,
  @NotNull HighlightInfo.HighlightSymbol type
) implements Comparable<HighlightInfo> {
  @Override public int compareTo(@NotNull HighlightInfo o) {
    return sourcePos.compareTo(o.sourcePos);
  }

  public enum DefKind {
    Data, Con, Struct, Field, Fn, Prim,
    Generalized, LocalVar, Module,
    Unknown;

    public @NotNull HighlightInfo toRef(@NotNull SourcePos sourcePos, @NotNull LinkId linkId) {
      return new HighlightInfo(sourcePos, new HighlightInfo.SymRef(linkId, this));
    }

    public @NotNull HighlightInfo toDef(@NotNull SourcePos sourcePos, @NotNull LinkId linkId) {
      return new HighlightInfo(sourcePos, new HighlightInfo.SymDef(linkId, this));
    }
  }

  public enum LitKind {
    Int, String, Keyword;

    public @NotNull HighlightInfo toLit(@NotNull SourcePos sourcePos) {
      return new HighlightInfo(sourcePos, new HighlightInfo.SymLit(this));
    }
  }

  public sealed interface HighlightSymbol {
  }

  /** A reference to a symbol */
  public record SymRef(@NotNull LinkId target, @NotNull HighlightInfo.DefKind kind) implements HighlightSymbol {
  }

  /** A definition of a symbol */
  public record SymDef(@NotNull LinkId target, @NotNull HighlightInfo.DefKind kind) implements HighlightSymbol {
  }

  /** An error element */
  public record SymError(@NotNull AyaDocile docile) implements HighlightSymbol {
  }

  /** A literal */
  public record SymLit(@NotNull HighlightInfo.LitKind kind) implements HighlightSymbol {
  }
}
