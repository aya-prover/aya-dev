// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.Expr;
import org.aya.core.def.GenericDef;
import org.aya.generic.ExprProblem;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.util.distill.DistillerOptions;
import org.jetbrains.annotations.NotNull;

public interface LiteralError extends TyckError {
  record AmbiguousLit(
    @Override @NotNull Expr expr,
    @NotNull ImmutableSeq<GenericDef> defs
  ) implements LiteralError, ExprProblem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.vcat(
        Doc.english("More than one types can be used to encode the literal:"),
        Doc.par(1, expr.toDoc(options)),
        Doc.plain("Candidates:"),
        Doc.par(1, Doc.join(Doc.plain(", "), defs.map(d -> Doc.styled(Style.code(), d.ref().name()))))
      );
    }

    @Override public @NotNull Doc hint(@NotNull DistillerOptions options) {
      return Doc.english("Specify the type explicitly can resolve the ambiguity.");
    }
  }
}
