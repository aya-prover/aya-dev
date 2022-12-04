// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import org.aya.concrete.Expr;
import org.aya.generic.AyaDocile;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    public @NotNull HighlightInfo toRef(@NotNull SourcePos sourcePos, int target, @Nullable Expr.WithTerm term) {
      return new HighlightInfo(sourcePos, new HighlightInfo.SymRef(target, this, term));
    }

    public @NotNull HighlightInfo toDef(@NotNull SourcePos sourcePos, int target, @Nullable Expr.WithTerm term) {
      return new HighlightInfo(sourcePos, new HighlightInfo.SymDef(target, this, term));
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
  public record SymRef(
    int target,
    @NotNull HighlightInfo.DefKind kind,
    @Nullable Expr.WithTerm term
  ) implements HighlightSymbol {
  }

  /** A definition of a symbol */
  public record SymDef(
    int target,
    @NotNull HighlightInfo.DefKind kind,
    @Nullable Expr.WithTerm term
  ) implements HighlightSymbol {
  }

  /** An error element */
  public record SymError(@NotNull AyaDocile docile) implements HighlightSymbol {
  }

  /** A literal */
  public record SymLit(@NotNull HighlightInfo.LitKind kind) implements HighlightSymbol {
  }
}
