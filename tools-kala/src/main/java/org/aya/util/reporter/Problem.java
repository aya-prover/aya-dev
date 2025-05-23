// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.reporter;

import java.util.Map;
import java.util.stream.Collectors;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.pretty.backend.terminal.UnixTermStyle;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.pretty.doc.Styles;
import org.aya.util.PrettierOptions;
import org.aya.util.error.PrettyError;
import org.aya.util.position.SourcePos;
import org.aya.util.position.WithPos;
import org.jetbrains.annotations.NotNull;

public interface Problem {
  enum Severity {
    ERROR,
    GOAL,
    WARN,
    INFO,
  }

  enum Stage {
    TERCK,
    TYCK,
    RESOLVE,
    PARSE,
    OTHER
  }

  @NotNull SourcePos sourcePos();
  @NotNull Doc describe(@NotNull PrettierOptions options);
  @NotNull Severity level();
  default @NotNull Stage stage() { return Stage.OTHER; }
  default @NotNull Doc hint(@NotNull PrettierOptions options) { return Doc.empty(); }
  default @NotNull SeqView<WithPos<Doc>> inlineHints(@NotNull PrettierOptions options) { return SeqView.empty(); }
  default boolean isError() { return level() == Severity.ERROR || level() == Severity.GOAL; }

  default @NotNull PrettyError toPrettyError(
    @NotNull PrettierOptions options,
    @NotNull PrettyError.FormatConfig prettyErrorConf
  ) {
    var sourcePos = sourcePos();
    return new PrettyError(
      sourcePos,
      brief(options),
      prettyErrorConf,
      inlineHints(options)
        .collect(Collectors.groupingBy(WithPos::sourcePos,
          Collectors.mapping(WithPos::data, Seq.factory())))
        .entrySet()
        .stream()
        .sorted(Map.Entry.comparingByKey())
        .map(kv -> new PrettyError.PosDoc(kv.getKey(), Doc.commaList(kv.getValue())))
        .collect(ImmutableSeq.factory())
    );
  }

  default @NotNull Doc brief(@NotNull PrettierOptions options) {
    var tag = switch (level()) {
      case WARN -> Doc.plain("Warning:");
      case GOAL -> Doc.plain("Goal:");
      case INFO -> Doc.plain("Info:");
      case ERROR -> Doc.styled(ERROR, "Error:");
    };
    var doc = Doc.sep(tag, Doc.align(describe(options)));
    var hint = hint(options);
    return hint instanceof Doc.Empty ? doc : Doc.vcat(
      doc,
      Doc.sep(Doc.styled(NOTE, "note:"), Doc.align(hint))
    );
  }

  @NotNull Styles ERROR = Style.bold().and().custom(UnixTermStyle.TerminalRed);
  @NotNull Styles NOTE = Style.bold().and().custom(UnixTermStyle.TerminalGreen);
}
