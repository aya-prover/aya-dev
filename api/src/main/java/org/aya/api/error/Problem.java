// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.api.error;

import kala.collection.Seq;
import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Tuple;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.util.WithPos;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.error.PrettyError;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author ice1000
 */
public interface Problem {
  enum Severity {
    INFO,
    GOAL,
    ERROR,
    WARN,
  }

  enum Stage {
    TERCK,
    TYCK,
    RESOLVE,
    PARSE,
    OTHER
  }

  @NotNull SourcePos sourcePos();
  /** @see Problem#computeFullErrorMessage(DistillerOptions, boolean) */
  @NotNull Doc describe(@NotNull DistillerOptions options);
  @NotNull Severity level();
  default @NotNull Stage stage() {
    return Stage.OTHER;
  }
  default @NotNull Doc hint() {
    return Doc.empty();
  }
  default @NotNull SeqLike<WithPos<Doc>> inlineHints(@NotNull DistillerOptions options) {
    return ImmutableSeq.empty();
  }

  default boolean isError() {
    return level() == Severity.ERROR;
  }

  default @NotNull PrettyError toPrettyError(@NotNull DistillerOptions options) {
    var sourcePos = sourcePos();
    return new PrettyError(
      sourcePos.file().name(),
      sourcePos.toSpan(),
      brief(options),
      inlineHints(options).stream()
        .collect(Collectors.groupingBy(WithPos::sourcePos,
          Collectors.mapping(WithPos::data, Seq.factory())))
        .entrySet()
        .stream()
        .sorted(Map.Entry.comparingByKey())
        .map(kv -> Tuple.of(kv.getKey().toSpan(), Doc.commaList(kv.getValue())))
        .collect(ImmutableSeq.factory())
    );
  }

  default @NotNull Doc brief(@NotNull DistillerOptions options) {
    var tag = switch (level()) {
      case WARN -> Doc.plain("Warning:");
      case GOAL -> Doc.plain("Goal:");
      case INFO -> Doc.plain("Info:");
      case ERROR -> Doc.plain("Error:");
    };
    var doc = Doc.sep(tag, Doc.align(describe(options)));
    var hint = hint();
    return hint instanceof Doc.Empty ? doc : Doc.vcat(
      doc,
      Doc.sep(Doc.plain("note:"), Doc.align(hint))
    );
  }

  int PAGE_WIDTH = 80;
  default @NotNull String computeFullErrorMessage(@NotNull DistillerOptions options, boolean unicode) {
    if (sourcePos() == SourcePos.NONE) return describe(options).renderWithPageWidth(PAGE_WIDTH, unicode);
    return toPrettyError(options).toDoc().renderWithPageWidth(PAGE_WIDTH, unicode);
  }

  default @NotNull String computeBriefErrorMessage(@NotNull DistillerOptions options) {
    return brief(options).commonRender();
  }
}
