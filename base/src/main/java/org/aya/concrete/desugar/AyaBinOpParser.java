// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.desugar;

import kala.collection.SeqView;
import org.aya.api.ref.DefVar;
import org.aya.concrete.Expr;
import org.aya.concrete.desugar.error.OperatorProblem;
import org.aya.concrete.stmt.Signatured;
import org.aya.pretty.doc.Doc;
import org.aya.util.binop.Assoc;
import org.aya.util.binop.BinOpSet;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AyaBinOpParser extends BinOpParser<AyaBinOpSet> {
  public AyaBinOpParser(@NotNull AyaBinOpSet opSet, @NotNull SeqView<@NotNull Elem> seq) {
    super(opSet, seq);
  }

  private static final Elem OP_APP = new Elem(
    BinOpSet.APP_ELEM.name(),
    new Expr.ErrorExpr(SourcePos.NONE, Doc.english("fakeApp escaped from BinOpParser")),
    true
  );

  @Override protected @NotNull Elem appOp() {
    return OP_APP;
  }

  @Override protected @NotNull BinOpParser<AyaBinOpSet> replicate(@NotNull SeqView<@NotNull Elem> seq) {
    return new AyaBinOpParser(opSet, seq);
  }

  @Override protected void reportAmbiguousPred(String op1, String op2, SourcePos pos) {
    opSet.reporter.report(new OperatorProblem.AmbiguousPredError(op1, op2, pos));
  }

  @Override protected @NotNull Expr createErrorExpr(@NotNull SourcePos sourcePos) {
    return new Expr.ErrorExpr(sourcePos, Doc.english("an application"));
  }

  @Override protected void reportFixityError(Assoc top, Assoc current, String op2, String op1, SourcePos pos) {
    opSet.reporter.report(new OperatorProblem.FixityError(op1, current, op2, top, pos));
  }

  @Override protected int argc(@NotNull Elem elem) {
    if (elem == appOp()) return 2;
    if (asOpDecl(elem) instanceof Signatured sig)
      return sig.telescope.view().count(Expr.Param::explicit);
    throw new IllegalArgumentException("not an operator");
  }

  @Override protected @Nullable OpDecl asOpDecl(@NotNull Elem elem) {
    if (elem.expr() instanceof Expr.RefExpr ref
      && ref.resolvedVar() instanceof DefVar<?, ?> defVar
      && defVar.concrete instanceof OpDecl opDecl
    ) return opDecl;
    return null;
  }
}
