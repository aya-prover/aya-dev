// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import kala.collection.immutable.ImmutableSeq;
import kala.text.StringSlice;
import org.aya.cli.literate.HighlightInfo.LitKind;
import org.aya.pretty.doc.Link;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public record FlclToken(
  @NotNull SourcePos range,
  @NotNull Type type
) {
  public static final @NotNull Link EMPTY_LINK = Link.page("");

  public record File(
    @NotNull ImmutableSeq<FlclToken> tokens,
    @NotNull StringSlice sourceCode,
    int startIndex
  ) {}

  public enum Type {
    Keyword, Fn, Data, Constructor, Primitive,
    Number, Local, Comment, WhiteSpace, Eol, Symbol
  }

  public @NotNull HighlightInfo toInfo() {
    return switch (type) {
      case Keyword -> new HighlightInfo.Lit(range, LitKind.Keyword);
      case Number -> new HighlightInfo.Lit(range, LitKind.Int);
      case Comment -> new HighlightInfo.Lit(range, LitKind.Comment);
      case Symbol -> new HighlightInfo.Lit(range, LitKind.SpecialSymbol);
      case WhiteSpace -> new HighlightInfo.Lit(range, LitKind.Whitespace);
      case Eol -> new HighlightInfo.Lit(range, LitKind.Eol);
      case Fn -> createRef(HighlightInfo.DefKind.Fn);
      case Data -> createRef(HighlightInfo.DefKind.Data);
      case Constructor -> createRef(HighlightInfo.DefKind.Con);
      case Primitive -> createRef(HighlightInfo.DefKind.Prim);
      case Local -> createRef(HighlightInfo.DefKind.LocalVar);
    };
  }

  private @NotNull HighlightInfo.Ref createRef(HighlightInfo.@NotNull DefKind kind) {
    return new HighlightInfo.Ref(range, EMPTY_LINK, kind, null);
  }
}
