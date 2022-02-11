// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.util.distill.DistillerOptions;
import org.aya.util.reporter.Problem;
import org.aya.concrete.stmt.Decl;
import org.aya.pretty.doc.Doc;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public sealed interface PrimProblem extends Problem {

  @Override default @NotNull Severity level() {
    return Severity.ERROR;
  }

  record NoResultTypeError(@NotNull Decl.PrimDecl prim) implements PrimProblem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(prim.toDoc(options),
        Doc.english("is expected to have a type"));
    }

    @Override public @NotNull SourcePos sourcePos() {
      return prim.sourcePos();
    }
  }
}
