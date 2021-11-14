// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.desugar;

import kala.collection.SeqView;
import org.aya.api.ref.DefVar;
import org.aya.concrete.Expr;
import org.aya.concrete.desugar.error.OperatorProblem;
import org.aya.concrete.stmt.Signatured;
import org.aya.generic.Constants;
import org.aya.pretty.doc.Doc;
import org.aya.util.binop.Assoc;
import org.aya.util.binop.BinOpParser;
import org.aya.util.binop.BinOpSet;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public final class BinExprParser extends BinOpParser<AyaBinOpSet, Expr, Expr.NamedArg> {
  public BinExprParser(@NotNull AyaBinOpSet opSet, @NotNull SeqView<@NotNull Elem<Expr>> seq) {
    super(opSet, seq);
  }

  private static final Elem<Expr> OP_APP = new Elem<>(
    BinOpSet.APP_ELEM.name(),
    new Expr.ErrorExpr(SourcePos.NONE, Doc.english("fakeApp escaped from BinOpParser")),
    true
  );

  @Override protected @NotNull Elem<Expr> appOp() {
    return OP_APP;
  }

  @Override protected @NotNull BinOpParser<AyaBinOpSet, Expr, Expr.NamedArg>
  replicate(@NotNull SeqView<@NotNull Elem<Expr>> seq) {
    return new BinExprParser(opSet, seq);
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

  @Override protected int argc(@NotNull OpDecl opDecl) {
    if (opDecl instanceof Signatured sig) return sig.telescope.view().count(Expr.Param::explicit);
    throw new IllegalArgumentException("not an operator");
  }

  @Override protected @Nullable OpDecl asOpDecl(@NotNull Elem<Expr> elem) {
    if (elem.expr() instanceof Expr.RefExpr ref
      && ref.resolvedVar() instanceof DefVar<?, ?> defVar
      && defVar.concrete instanceof OpDecl opDecl
    ) return opDecl;
    return null;
  }

  @Override protected @NotNull Expr
  makeApp(@NotNull SourcePos sourcePos, @NotNull Expr function, @NotNull Expr.NamedArg arg) {
    return new Expr.AppExpr(sourcePos, function, arg);
  }

  @Override protected @NotNull Expr.NamedArg makeArg(@NotNull Elem<Expr> elem) {
    return new Expr.NamedArg(elem.explicit(), elem.name(), elem.expr());
  }

  @Override public @NotNull Expr makeSectionApp(
    @NotNull SourcePos pos, @NotNull Elem<Expr> op, @NotNull Function<Elem<Expr>, Expr> lamBody
  ) {
    var missing = Constants.randomlyNamed(op.expr().sourcePos());
    var missingElem = new Elem<Expr>(new Expr.RefExpr(SourcePos.NONE, missing), true);
    var missingParam = new Expr.Param(missing.definition(), missing, true);
    return new Expr.LamExpr(pos, missingParam, lamBody.apply(missingElem));
  }
}
