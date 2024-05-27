// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Link;
import org.aya.producer.flcl.FlclToken;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;

public record FlclFaithfulPrettier(@Override @NotNull PrettierOptions options)
  implements FaithfulPrettier {
  public static final @NotNull Link EMPTY_LINK = Link.page("");
  public static @NotNull HighlightInfo toInfo(FlclToken flclToken) {
    final var range = flclToken.range();
    return switch (flclToken.type()) {
      case Keyword -> new HighlightInfo.Lit(range, HighlightInfo.LitKind.Keyword);
      case Number -> new HighlightInfo.Lit(range, HighlightInfo.LitKind.Int);
      case Comment -> new HighlightInfo.Lit(range, HighlightInfo.LitKind.Comment);
      case Symbol -> new HighlightInfo.Lit(range, HighlightInfo.LitKind.SpecialSymbol);
      case WhiteSpace -> new HighlightInfo.Lit(range, HighlightInfo.LitKind.Whitespace);
      case Eol -> new HighlightInfo.Lit(range, HighlightInfo.LitKind.Eol);
      case Fn -> createRef(range, HighlightInfo.DefKind.Fn);
      case Data -> createRef(range, HighlightInfo.DefKind.Data);
      case Constructor -> createRef(range, HighlightInfo.DefKind.Con);
      case Primitive -> createRef(range, HighlightInfo.DefKind.Prim);
      case Local -> createRef(range, HighlightInfo.DefKind.LocalVar);
    };
  }
  private static @NotNull HighlightInfo.Ref createRef(@NotNull SourcePos range, HighlightInfo.@NotNull DefKind kind) {
    return new HighlightInfo.Ref(range, EMPTY_LINK, kind, null);
  }
  public @NotNull Doc highlight(@NotNull FlclToken.File file) {
    var highlights = file.tokens().view().map(FlclFaithfulPrettier::toInfo).sorted().toImmutableSeq();
    FaithfulPrettier.checkHighlights(highlights);
    return doHighlight(file.sourceCode(), file.startIndex(), highlights);
  }
}
