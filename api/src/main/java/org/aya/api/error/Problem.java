// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.error;

import org.aya.api.util.WithPos;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.error.PrettyError;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.tuple.Tuple;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
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
  @NotNull Doc describe();
  @NotNull Severity level();
  default @NotNull Stage stage() {
    return Stage.OTHER;
  }
  default @NotNull Doc hint() {
    return Doc.empty();
  }
  default @NotNull SeqLike<WithPos<Doc>> inlineHints() {
    return ImmutableSeq.empty();
  }

  default @NotNull PrettyError toPrettyError() {
    var sourcePos = sourcePos();
    return new PrettyError(
      sourcePos.file().name(),
      sourcePos.toSpan(),
      brief(),
      inlineHints().stream()
        .collect(Collectors.groupingBy(WithPos::sourcePos,
          Collectors.mapping(WithPos::data, Seq.factory())))
        .entrySet()
        .stream()
        .sorted(Comparator.comparing(entry -> entry.getKey().tokenStartIndex()))
        .map(kv -> Tuple.of(kv.getKey().toSpan(), Doc.join(Doc.plain(", "), kv.getValue())))
        .collect(Seq.factory())
    );
  }

  default @NotNull Doc brief() {
    var tag = switch (level()) {
      case WARN -> Doc.plain("Warning:");
      case GOAL -> Doc.plain("Goal:");
      case INFO -> Doc.plain("Info:");
      case ERROR -> Doc.plain("Error:");
    };
    var doc = Doc.hsep(tag, Doc.align(describe()));
    var hint = hint();
    return hint instanceof Doc.Empty ? doc : Doc.vcat(
      doc,
      Doc.hsep(Doc.plain("note:"), Doc.align(hint))
    );
  }

  default @NotNull String errorMsg() {
    if (sourcePos() == SourcePos.NONE)
      return describe().debugRender();
    var error = toPrettyError().toDoc();
    return error.renderWithPageWidth(120);
  }

  default @NotNull String briefErrorMsg() {
    return brief().renderWithPageWidth(120);
  }
}
