// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import kala.collection.mutable.MutableList;
import org.aya.prettier.BasePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.DefVar;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.aya.util.reporter.Problem;
import org.aya.util.terck.Diagonal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record BadRecursion(
  @Override @NotNull SourcePos sourcePos, @NotNull DefVar<?, ?> name,
  @Nullable Diagonal<? extends Term, TyckDef> diag
) implements Problem {
  @Override public @NotNull Severity level() { return Severity.ERROR; }
  @Override public @NotNull Stage stage() { return Stage.TERCK; }

  @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
    return Doc.sep(Doc.english("The recursive definition"),
      Doc.code(BasePrettier.defVar(name)),
      Doc.english("is not structurally recursive"));
  }

  @Override public @NotNull Doc hint(@NotNull PrettierOptions options) {
    if (diag == null) return Doc.empty();
    var matrix = diag.matrix();
    var buffer = MutableList.of(
      Doc.english("In particular, the problematic call is:"),
      Doc.nest(2, matrix.callable().toDoc(options)),
      Doc.english("whose call matrix is:"),
      matrix.toDoc());
    if (diag.matrix().rows() > 1) {
      buffer.append(Doc.english("whose diagonal is:"));
      buffer.append(diag.toDoc());
    }
    return Doc.vcat(buffer);
  }
}
