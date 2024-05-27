// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import kala.collection.immutable.ImmutableSeq;
import kala.text.StringSlice;
import org.aya.cli.utils.InlineHintProblem;
import org.aya.literate.Literate;
import org.aya.literate.LiterateConsumer;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.literate.AyaLiterate;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

/**
 * This prettier maintains all highlights created from {@link SyntaxHighlight} and all
 * problems reported by Aya compiler.
 * Implementation-wise, this prettier can be seen as a highlight server for a single file.
 * <p>
 * When the highlight of a code block is requested, it filters out
 * all highlights and problems that belong to the code block, and then
 * build a {@link Doc} containing the highlighted source code mixed with compiler
 * outputs, as done in {@link #highlight(String, SourcePos)}.
 *
 * @param problems   All problems of a single file
 * @param highlights All highlights of a single file
 */
public record LiterateFaithfulPrettier(
  @NotNull ImmutableSeq<Problem> problems,
  @NotNull ImmutableSeq<HighlightInfo> highlights,
  @Override @NotNull PrettierOptions options
) implements LiterateConsumer, FaithfulPrettier {
  /**
   * Highlight all visible aya code blocks
   */
  @Override public void accept(@NotNull Literate literate) {
    if (literate instanceof AyaLiterate.AyaVisibleCodeBlock code && code.sourcePos != null) {
      code.highlighted = highlight(code.code, code.sourcePos);
    }
    LiterateConsumer.super.accept(literate);
  }


  /** find highlights and problems inside the code range, and merge them as new highlights */
  private static @NotNull ImmutableSeq<HighlightInfo> merge(
    @NotNull SourcePos codeRange,
    @NotNull PrettierOptions options,
    @NotNull ImmutableSeq<HighlightInfo> highlights,
    @NotNull ImmutableSeq<Problem> problems
  ) {
    var problemsInRange = problems.view()
      .filter(p -> codeRange.containsIndex(p.sourcePos()))
      .flatMap(p -> InlineHintProblem.withInlineHints(p, options))
      .distinct()
      .toImmutableSeq();

    return problemsInRange.foldLeft(FaithfulPrettier.highlightsInRange(codeRange, highlights), (acc, p) -> {
      var partition = acc.partition(
        h -> p.sourcePos().containsIndex(h.sourcePos()));
      var inP = partition.component1().sorted();
      var wrap = new HighlightInfo.Err(p, inP);
      return partition.component2().appended(wrap);
    });
  }

  /**
   * Apply highlights to source code string.
   *
   * @param raw       the source code
   * @param codeRange where the raw start from (the 'raw' might be a piece of the source code,
   *                  so it probably not starts from 0).
   */
  public @NotNull Doc highlight(@NotNull String raw, @NotNull SourcePos codeRange) {
    var merged = merge(codeRange, options(), highlights, problems).sorted();
    FaithfulPrettier.checkHighlights(merged);
    return doHighlight(StringSlice.of(raw), codeRange.tokenStartIndex(), merged);
  }
}
