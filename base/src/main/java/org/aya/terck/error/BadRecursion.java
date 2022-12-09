// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck.error;

import kala.collection.mutable.MutableList;
import org.aya.core.def.Def;
import org.aya.core.term.Callable;
import org.aya.core.term.Term;
import org.aya.distill.BaseDistiller;
import org.aya.pretty.doc.Doc;
import org.aya.ref.DefVar;
import org.aya.terck.Diagonal;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record BadRecursion(
  @Override @NotNull SourcePos sourcePos, @NotNull DefVar<?, ?> name,
  @Nullable Diagonal<Callable, Def, Term.Param> diag
) implements Problem {
  @Override public @NotNull Severity level() {return Severity.ERROR;}

  @Override public @NotNull Stage stage() {return Stage.TERCK;}

  @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
    return Doc.sep(Doc.english("The recursive definition"),
      Doc.code(BaseDistiller.defVar(name)),
      Doc.english("is not structurally recursive"));
  }

  @Override public @NotNull Doc hint(@NotNull DistillerOptions options) {
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
