// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import kala.collection.mutable.MutableList;
import org.aya.concrete.Expr;
import org.aya.core.term.Term;
import org.aya.generic.ExprProblem;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.tycker.TyckState;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;

public sealed interface IrrElimProblem extends TyckError {
  record Proj(
    @Override @NotNull Expr expr,
    @NotNull Term projectee,
    @NotNull Term projecteeType,
    @NotNull Term projectedType,
    @NotNull TyckState state
  ) implements ExprProblem, IrrElimProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      var list = MutableList.of(Doc.english("Relevant projection of type:"));
      UnifyInfo.exprInfo(projectedType, options, state, list);
      list.append(Doc.sep(Doc.plain("from"), Doc.code(projectee.toDoc(options)), Doc.english("of type:")));
      UnifyInfo.exprInfo(projecteeType, options, state, list);
      list.append(Doc.english("This is not allowed."));
      return Doc.vcat(list);
    }
  }
}
