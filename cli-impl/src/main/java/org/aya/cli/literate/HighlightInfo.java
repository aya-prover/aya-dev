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

import java.util.Objects;

public sealed interface HighlightInfo extends Comparable<HighlightInfo> {
  @NotNull SourcePos sourcePos();

  default @Override int compareTo(@NotNull HighlightInfo o) {
    return sourcePos().compareTo(o.sourcePos());
  }

  enum DefKind {
    Data, Con, Clazz, Member, Fn, Prim,
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
  ) implements HighlightInfo {
    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Ref ref = (Ref) o;
      return Objects.equals(sourcePos, ref.sourcePos) && Objects.equals(target, ref.target) && kind == ref.kind;
    }

    @Override public int hashCode() {
      return Objects.hash(sourcePos, target, kind);
    }
  }

  /** A definition of a symbol */
  record Def(
    @NotNull SourcePos sourcePos,
    @NotNull Link target,
    @NotNull DefKind kind,
    @Nullable AyaDocile type
  ) implements HighlightInfo {
    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Def def = (Def) o;
      return Objects.equals(sourcePos, def.sourcePos) && Objects.equals(target, def.target) && kind == def.kind;
    }

    @Override public int hashCode() {
      return Objects.hash(sourcePos, target, kind);
    }
  }

  record Err(
    @NotNull Problem problem,
    @NotNull ImmutableSeq<HighlightInfo> children
  ) implements HighlightInfo {
    @Override public @NotNull SourcePos sourcePos() { return problem.sourcePos(); }
  }

  /** A literal */
  record Lit(@NotNull SourcePos sourcePos, @NotNull LitKind kind) implements HighlightInfo { }
}
