// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.AyaDocile;
import org.aya.pretty.doc.Link;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface HighlightInfo extends Comparable<HighlightInfo> {
  @NotNull SourcePos sourcePos();

  default @Override int compareTo(@NotNull HighlightInfo o) {
    return sourcePos().compareTo(o.sourcePos());
  }

  enum DefKind {
    Data, Con, Struct, Field, Fn, Prim,
    Generalized, LocalVar, Module,
    Unknown;

    public @NotNull HighlightInfo toRef(@NotNull SourcePos sourcePos, @NotNull Link target, @Nullable AyaDocile type) {
      return new Ref(sourcePos, target, this, type);
    }

    public @NotNull HighlightInfo toDef(@NotNull SourcePos sourcePos, @NotNull Link target, @Nullable AyaDocile type) {
      return new Def(sourcePos, target, this, type);
    }
  }

  enum LitKind {
    Int, String, Keyword, Comment, SpecialSymbol, Eol, Whitespace;

    public @NotNull HighlightInfo toLit(@NotNull SourcePos sourcePos) {
      return new Lit(sourcePos, this);
    }
  }

  /** A reference to a symbol */
  record Ref(
    @NotNull SourcePos sourcePos,
    @NotNull Link target,
    @NotNull DefKind kind,
    @Nullable AyaDocile type
  ) implements HighlightInfo {}

  /** A definition of a symbol */
  record Def(
    @NotNull SourcePos sourcePos,
    @NotNull Link target,
    @NotNull DefKind kind,
    @Nullable AyaDocile type
  ) implements HighlightInfo {}

  record Err(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<HighlightInfo> children,
    @NotNull Problem problem
  ) implements HighlightInfo {}

  /** A literal */
  record Lit(@NotNull SourcePos sourcePos, @NotNull LitKind kind) implements HighlightInfo {}
}
