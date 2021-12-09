// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck.error;

import kala.collection.SeqLike;
import kala.collection.SeqView;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.Problem;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;

public record NonTerminating(@NotNull SourcePos sourcePos, @NotNull String name,
                             @NotNull SourcePos callPos) implements Problem {
  @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
    return Doc.sep(Doc.english("The recursive definition"),
      Doc.styled(Style.code(), name),
      Doc.english("is not structurally recursive"));
  }

  @Override public @NotNull SeqLike<WithPos<Doc>> inlineHints(@NotNull DistillerOptions options) {
    return callPos != SourcePos.NONE && callPos != SourcePos.SER
      ? SeqView.of(new WithPos<>(callPos, Doc.english("The non-terminating call happens here")))
      : SeqView.empty();
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
