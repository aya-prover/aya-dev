// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.resolve.error;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.aya.core.def.PrimDef;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;

/**
 * @author darkflames
 */
public record PrimDependencyError(
  @NotNull String name,
  @NotNull ImmutableSeq<PrimDef.ID> lack,
  @Override @NotNull SourcePos sourcePos
) implements ResolveProblem {
  @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
    assert lack.isNotEmpty();
    return Doc.sep(
      Doc.plain("The prim"), Doc.styled(Style.code(), Doc.plain(name)),
      Doc.english("depends on undeclared prims:"),
      Doc.commaList(lack.map(name -> Doc.styled(Style.code(), Doc.plain(name.id)))));
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
