// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.desugar;

import kala.collection.SeqView;
import org.aya.concrete.Expr;
import org.aya.concrete.error.OperatorError;
import org.aya.generic.Constants;
import org.aya.pretty.doc.Doc;
import org.aya.ref.DefVar;
import org.aya.resolve.ResolveInfo;
import org.aya.util.binop.Assoc;
import org.aya.util.binop.BinOpParser;
import org.aya.util.binop.BinOpSet;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public final class BinExprParser extends BinOpParser<AyaBinOpSet, Expr, Expr.NamedArg> {
  private final @NotNull ResolveInfo resolveInfo;

  public BinExprParser(@NotNull ResolveInfo resolveInfo, @NotNull SeqView<Expr.@NotNull NamedArg> seq) {
    super(resolveInfo.opSet(), seq);
    this.resolveInfo = resolveInfo;
  }

  private static final Expr.NamedArg OP_APP = new Expr.NamedArg(
    true,
    BinOpSet.APP_ELEM.name(),
    new Expr.Error(SourcePos.NONE, Doc.english("fakeApp escaped from BinOpParser"))
  );

  @Override protected @NotNull Expr.NamedArg appOp() {
    return OP_APP;
  }

  @Override protected @NotNull BinOpParser<AyaBinOpSet, Expr, Expr.NamedArg>
  replicate(@NotNull SeqView<Expr.@NotNull NamedArg> seq) {
    return new BinExprParser(resolveInfo, seq);
  }

  @Override protected void reportAmbiguousPred(String op1, String op2, SourcePos pos) {
    opSet.reporter.report(new OperatorError.Precedence(op1, op2, pos));
  }

  @Override protected @NotNull Expr createErrorExpr(@NotNull SourcePos sourcePos) {
    return new Expr.Error(sourcePos, Doc.english("an application"));
  }

  @Override protected void reportFixityError(Assoc top, Assoc current, String topOp, String currentOp, SourcePos pos) {
    opSet.reporter.report(new OperatorError.Fixity(currentOp, current, topOp, top, pos));
  }

  @Override protected void reportMissingOperand(String op, SourcePos pos) {
    opSet.reporter.report(new OperatorError.MissingOperand(pos, op));
  }

  @Override protected @Nullable OpDecl underlyingOpDecl(@NotNull Expr.NamedArg elem) {
    var expr = elem.term();
    while (expr instanceof Expr.Lift lift) expr = lift.expr();
    return expr instanceof Expr.Ref ref && ref.resolvedVar() instanceof DefVar<?, ?> defVar
      ? defVar.resolveOpDecl(resolveInfo.thisModule().modulePath())
      : null;
  }

  @Override protected @NotNull Expr.NamedArg
  makeArg(@NotNull SourcePos pos, @NotNull Expr func, Expr.@NotNull NamedArg arg, boolean explicit) {
    return new Expr.NamedArg(explicit, new Expr.App(pos, func, arg));
  }

  @Override public @NotNull Expr.NamedArg makeSectionApp(
    @NotNull SourcePos pos, Expr.@NotNull NamedArg op, @NotNull Function<Expr.NamedArg, Expr> lamBody
  ) {
    var missing = Constants.randomlyNamed(op.term().sourcePos());
    var missingElem = new Expr.NamedArg(true, new Expr.Ref(SourcePos.NONE, missing));
    var missingParam = new Expr.Param(missing.definition(), missing, true);
    var term = new Expr.Lambda(pos, missingParam, lamBody.apply(missingElem));
    return new Expr.NamedArg(op.explicit(), term);
  }
}
