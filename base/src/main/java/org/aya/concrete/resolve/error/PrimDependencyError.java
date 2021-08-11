// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.error;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;

public record PrimDependencyError(
  @NotNull String name,
  @NotNull ImmutableSeq<String> lack,
  @NotNull SourcePos sourcePos
) implements Problem {
  @Override
  public @NotNull Doc describe() {
    assert lack.size() > 0;
    return Doc.cat(
      primName(name), Doc.plain(" depends on "),
      Doc.vcat(lack.map(PrimDependencyError::primName)),
      Doc.plain(", but " + (lack.size() == 1 ? "it is" : "they are") +" not declared.")
    );
  }

  private static @NotNull Doc primName(@NotNull String name) {
    return Doc.cat(Doc.plain("Prim "), Doc.styled(Style.code(), Doc.plain(name)));
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
