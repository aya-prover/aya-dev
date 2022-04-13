// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.concrete.stmt.Decl;
import org.aya.core.def.FnDef;
import org.aya.distill.BaseDistiller;
import org.aya.pretty.doc.Doc;
import org.aya.ref.DefVar;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

public record NobodyError(
  @Override @NotNull SourcePos sourcePos,
  @NotNull DefVar<FnDef, Decl.FnDecl> var
) implements Problem {
  @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
    return Doc.sep(
      Doc.english("The empty pattern-matching function"),
      BaseDistiller.defVar(var),
      Doc.english("does not have a telescope"));
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
