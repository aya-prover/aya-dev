// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import com.intellij.openapi.util.text.StringUtil;
import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.text.StringSlice;
import org.aya.generic.AyaDocile;
import org.aya.prettier.BasePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record FaithfulPrettier(@NotNull PrettierOptions options) {
  private static void checkHighlights(@NotNull ImmutableSeq<HighlightInfo> highlights) {
    highlights.foldLeft(-1, (lastEndIndex, h) -> {
      var sp = h.sourcePos();
      if (!(sp.tokenStartIndex() <= sp.tokenEndIndex()))
        throw new IllegalArgumentException("Invalid source pos: " + sp);
      if (!(lastEndIndex < sp.tokenStartIndex()))
        throw new IllegalArgumentException("Intersect with previous source pos: " + sp);
      return sp.tokenEndIndex();
    });
  }

  /**
   * Apply highlights to source code string.
   *
   * @param raw        the source code
   * @param base       where the raw start from (the 'raw' might be a piece of the source code,
   *                   so it probably not starts from 0).
   * @param highlights the highlights for the source code
   */
  public @NotNull Doc highlight(@NotNull String raw, int base, @NotNull ImmutableSeq<HighlightInfo> highlights) {
    highlights = highlights.sorted().view()
      .distinct()
      .filter(h -> h.sourcePos() != SourcePos.NONE)
      .filterNot(h -> h.sourcePos().isEmpty())
      .toImmutableSeq();
    checkHighlights(highlights);

    return doHighlight(StringSlice.of(raw), base, highlights);
  }

  private @NotNull Doc doHighlight(@NotNull StringSlice raw, int base, @NotNull ImmutableSeq<HighlightInfo> highlights) {
    var docs = MutableList.<Doc>create();

    for (var current : highlights) {
      // Cut the `raw` text at `base` offset into three parts: before, current, and remaining,
      // which needs two split positions: `current.sourcePos().start` and `current.sourcePos().end`, respectively.
      var knifeCut = twoKnifeThreeParts(raw, base, current.sourcePos());
      // move forward
      raw = knifeCut.remaining;
      base = knifeCut.base;

      // If there's orphan text before the highlighted cut, add it to result as plain text.
      if (!knifeCut.before.isEmpty()) {
        // TODO: handle whitespaces in the lexer, and use a new highlight type for them.
        //  this workaround solution does not work for whitespace in LaTeX.
        docs.append(Doc.plain(knifeCut.before.toString()));
      }
      // Umm, I think it doesn't matter, `Doc.empty` is the unit of `Doc.cat`
      // Do not add to result if the highlighted cut contains nothing
      var highlight = highlightOne(knifeCut.current.toString(), current.type());
      if (highlight != Doc.empty())
        docs.append(highlight);
    }

    if (!raw.isEmpty()) docs.append(Doc.plain(raw.toString()));

    return Doc.cat(docs);
  }

  private @NotNull Doc highlightOne(@NotNull String raw, @NotNull HighlightInfo.HighlightSymbol highlight) {
    if (raw.isEmpty()) return Doc.empty();
    return switch (highlight) {
      case HighlightInfo.SymDef symDef ->
        Doc.linkDef(highlightVar(raw, symDef.kind()), symDef.target(), hover(symDef.type()));
      case HighlightInfo.SymRef symRef ->
        Doc.linkRef(highlightVar(raw, symRef.kind()), symRef.target(), hover(symRef.type()));
      case HighlightInfo.SymLit symLit -> highlightLit(raw, symLit.kind());
      case HighlightInfo.SymError symError -> Doc.plain(raw);   // TODO: any style for error?
    };
  }

  private @Nullable String hover(@Nullable AyaDocile term) {
    if (term == null) return null;
    return term.toDoc(options()).commonRender();
  }

  private @NotNull Doc highlightVar(@NotNull String raw, @NotNull HighlightInfo.DefKind defKind) {
    var style = switch (defKind) {
      case Data -> BasePrettier.DATA;
      case Con -> BasePrettier.CON;
      case Clazz -> BasePrettier.CLAZZ;
      case Member -> BasePrettier.MEMBER;
      case Fn -> BasePrettier.FN;
      case Prim -> BasePrettier.PRIM;
      case Generalized -> BasePrettier.GENERALIZED;
      case LocalVar -> BasePrettier.LOCAL_VAR;
      case Unknown, Module -> null;
    };
    return style != null ? Doc.styled(style, raw) : Doc.plain(raw);
  }

  private @NotNull Doc highlightLit(@NotNull String raw, @NotNull HighlightInfo.LitKind litKind) {
    return switch (litKind) {
      case Int, Whitespace -> Doc.plain(raw);
      case String -> Doc.plain(StringUtil.escapeStringCharacters(raw));
      case Keyword -> Doc.styled(BasePrettier.KEYWORD, Doc.symbol(raw));
      case Comment -> Doc.styled(BasePrettier.COMMENT, raw);
      case SpecialSymbol -> Doc.symbol(raw);
      case Eol -> Doc.cat(Seq.fill(raw.length(), Doc.line()));
    };
  }

  private static @NotNull KnifeCut twoKnifeThreeParts(@NotNull StringSlice raw, int base, @NotNull SourcePos twoKnife) {
    var beginPart1 = twoKnife.tokenStartIndex() - base;
    var endPart1 = twoKnife.tokenEndIndex() + 1 - base;
    var part0 = raw.subSequence(0, beginPart1);
    var part1 = raw.subSequence(beginPart1, endPart1);
    var part2 = raw.subSequence(endPart1, raw.length());
    return new KnifeCut(part0, part1, part2, twoKnife.tokenEndIndex() + 1);
  }

  record KnifeCut(
    @NotNull StringSlice before,
    @NotNull StringSlice current,
    @NotNull StringSlice remaining,
    int base
  ) {
  }
}
