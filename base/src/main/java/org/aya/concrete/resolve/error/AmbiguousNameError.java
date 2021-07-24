// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.error;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;

public record AmbiguousNameError(
  @NotNull String name,
  @NotNull ImmutableSeq<Seq<String>> disambiguation,
  @NotNull SourcePos sourcePos
) implements Problem {
  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }

  @Override public @NotNull Doc describe() {
    return Doc.vcat(Doc.cat(Doc.english("The unqualified name referred to by "), Doc.styled(Style.code(), Doc.plain(name)), Doc.english(" is ambiguous.")),
      Doc.english("Try using the following module names to qualify the name to disambiguate:"),
      Doc.styled(Style.code(), Doc.nest(1, Doc.vcat(disambiguation.map(a -> Doc.plain(a.joinToString("::")))))));
  }

  @Override public @NotNull Stage stage() {
    return Stage.RESOLVE;
  }
}
