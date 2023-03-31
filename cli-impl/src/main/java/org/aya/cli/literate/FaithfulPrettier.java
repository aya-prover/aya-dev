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
import org.aya.pretty.doc.Language;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record FaithfulPrettier(@NotNull ImmutableSeq<Problem> problems, @NotNull PrettierOptions options) {
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

  private static @NotNull ImmutableSeq<HighlightInfo> merge(
    @NotNull ImmutableSeq<HighlightInfo> highlights,
    @NotNull ImmutableSeq<Problem> problems
  ) {
    for (var p : problems) {
      var partition = highlights.partition(
        h -> p.sourcePos().containsIndex(h.sourcePos()));
      var inP = partition.component1();
      if (inP.isNotEmpty()) {
        var wrap = new HighlightInfo.Err(
          inP.view()
            .map(HighlightInfo::sourcePos)
            .reduce(SourcePos::union),
          inP.view().sorted().distinct().toImmutableSeq(),
          p
        );
        highlights = partition.component2().appended(wrap);
      } else {
        highlights = partition.component2();
      }
    }
    return highlights;
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
    highlights = highlights.view()
      .sorted().distinct()
      .filter(h -> h.sourcePos() != SourcePos.NONE)
      .filterNot(h -> h.sourcePos().isEmpty())
      .toImmutableSeq();
    checkHighlights(highlights);

    var merged = merge(highlights, problems).view()
      .sorted().distinct()
      .toImmutableSeq();
    checkHighlights(merged);

    return doHighlight(StringSlice.of(raw), base, merged);
  }

  private @NotNull Doc doHighlight(@NotNull StringSlice raw, int base, @NotNull ImmutableSeq<HighlightInfo> highlights) {
    var docs = MutableList.<Doc>create();

    for (var current : highlights) {
      // Cut the `raw` text at `base` offset into three parts: before, current, and remaining,
      // which needs two split positions: `current.sourcePos().start` and `current.sourcePos().end`, respectively.
      var knifeCut = twoKnifeThreeParts(raw, base, current.sourcePos());

      // If there's an orphan text before the highlighted cut, add it to the result as plain text.
      if (!knifeCut.before.isEmpty()) {
        docs.append(Doc.plain(knifeCut.before.toString()));
      }
      // `Doc.empty` is the unit of `Doc.cat`, so it is safe to add it to the result.
      var highlight = highlightOne(knifeCut.current.toString(), base, current);
      docs.append(highlight);

      // Move forward
      raw = knifeCut.remaining;
      base = knifeCut.base;
    }

    if (!raw.isEmpty()) docs.append(Doc.plain(raw.toString()));

    return Doc.cat(docs);
  }

  private @NotNull Doc highlightOne(@NotNull String raw, int base, @NotNull HighlightInfo highlight) {
    if (raw.isEmpty()) return Doc.empty();
    return switch (highlight) {
      case HighlightInfo.Def def -> Doc.linkDef(highlightVar(raw, def.kind()), def.target(), hover(def.type()));
      case HighlightInfo.Ref ref -> Doc.linkRef(highlightVar(raw, ref.kind()), ref.target(), hover(ref.type()));
      case HighlightInfo.Lit lit -> highlightLit(raw, lit.kind());
      case HighlightInfo.Err err -> {
        var doc = doHighlight(StringSlice.of(raw), base, err.children());
        var style = switch (err.problem().level()) {
          case ERROR -> BasePrettier.ERROR;
          case WARN -> BasePrettier.WARNING;
          case GOAL, INFO -> null;
        };
        yield style == null ? doc : new Doc.Tooltip(Doc.styled(style, doc), () -> Doc.codeBlock(
          Language.Builtin.Aya,
          err.problem().brief(options).toDoc()
        ));
      }
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
      case Struct -> BasePrettier.STRUCT;
      case Field -> BasePrettier.FIELD;
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
