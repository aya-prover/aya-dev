// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.resolve.error;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import org.aya.api.distill.DistillerOptions;
import org.aya.concrete.stmt.QualifiedID;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public record AmbiguousNameError(
  @NotNull String name,
  @NotNull ImmutableSeq<Seq<String>> disambiguation,
  @Override @NotNull SourcePos sourcePos
) implements ResolveProblem {
  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }

  @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
    return Doc.vcat(Doc.sep(
        Doc.english("The unqualified name"),
        Doc.styled(Style.code(), Doc.plain(name)),
        Doc.english("is ambiguous")),
      Doc.english("Use one of the following module names to qualify the name to disambiguate:"),
      Doc.styled(Style.code(), Doc.nest(1, Doc.vcat(disambiguation.view()
        .map(QualifiedID::join)
        .map(Doc::plain)))));
  }
}
