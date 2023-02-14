// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import org.aya.generic.AyaDocile;
import org.aya.pretty.doc.Link;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record HighlightInfo(
  @NotNull SourcePos sourcePos,
  @NotNull HighlightSymbol type
) implements Comparable<HighlightInfo> {
  @Override public int compareTo(@NotNull HighlightInfo o) {
    return sourcePos.compareTo(o.sourcePos);
  }

  public enum DefKind {
    Data, Con, Struct, Field, Fn, Prim,
    Generalized, LocalVar, Module,
    Unknown;

    public @NotNull HighlightInfo toRef(@NotNull SourcePos sourcePos, @NotNull Link target, @Nullable AyaDocile type) {
      return new HighlightInfo(sourcePos, new SymRef(target, this, type));
    }

    public @NotNull HighlightInfo toDef(@NotNull SourcePos sourcePos, @NotNull Link target, @Nullable AyaDocile type) {
      return new HighlightInfo(sourcePos, new SymDef(target, this, type));
    }
  }

  public enum LitKind {
    Int, String, Keyword, Comment, SpecialSymbol, Eol, Whitespace;

    public @NotNull HighlightInfo toLit(@NotNull SourcePos sourcePos) {
      return new HighlightInfo(sourcePos, new SymLit(this));
    }
  }

  public sealed interface HighlightSymbol {
    default @NotNull HighlightInfo toInfo(@NotNull SourcePos sourcePos) {
      return new HighlightInfo(sourcePos, this);
    }
  }

  /** A reference to a symbol */
  public record SymRef(@NotNull Link target, @NotNull DefKind kind, @Nullable AyaDocile type) implements HighlightSymbol {
  }

  /** A definition of a symbol */
  public record SymDef(@NotNull Link target, @NotNull DefKind kind, @Nullable AyaDocile type) implements HighlightSymbol {
  }

  /** An error element */
  public record SymError(@NotNull AyaDocile docile) implements HighlightSymbol {
  }

  /** A literal */
  public record SymLit(@NotNull LitKind kind) implements HighlightSymbol {
  }
}
