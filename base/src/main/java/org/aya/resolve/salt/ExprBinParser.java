// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.salt;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.Constants;
import org.aya.pretty.doc.Doc;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.error.OperatorError;
import org.aya.syntax.concrete.Expr;
import org.aya.util.binop.Assoc;
import org.aya.util.binop.BinOpParser;
import org.aya.util.binop.BinOpSet;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class ExprBinParser extends BinOpParser<AyaBinOpSet, WithPos<Expr>, Expr.NamedArg> {
  private final @NotNull ResolveInfo resolveInfo;

  public ExprBinParser(@NotNull ResolveInfo resolveInfo, @NotNull SeqView<Expr.@NotNull NamedArg> seq) {
    super(resolveInfo.opSet(), seq);
    this.resolveInfo = resolveInfo;
  }

  private static final Expr.NamedArg OP_APP = new Expr.NamedArg(
    true, BinOpSet.APP_ELEM.name(),
    new WithPos<>(SourcePos.NONE, new Expr.Error(Doc.english("fakeApp escaped from BinOpParser")))
  );

  @Override protected @NotNull Expr.NamedArg appOp() {
    return OP_APP;
  }

  @Override protected @NotNull BinOpParser<AyaBinOpSet, WithPos<Expr>, Expr.NamedArg>
  replicate(@NotNull SeqView<Expr.@NotNull NamedArg> seq) {
    return new ExprBinParser(resolveInfo, seq);
  }

  @Override protected void reportAmbiguousPred(String op1, String op2, SourcePos pos) {
    opSet.reporter.report(new OperatorError.Precedence(op1, op2, pos));
  }

  @Override protected @NotNull WithPos<Expr> createErrorExpr(@NotNull SourcePos sourcePos) {
    return new WithPos<>(sourcePos, new Expr.Error(Doc.english("an application")));
  }

  @Override protected void reportFixityError(Assoc top, Assoc current, String topOp, String currentOp, SourcePos pos) {
    opSet.reporter.report(new OperatorError.Fixity(currentOp, current, topOp, top, pos));
  }

  @Override protected void reportMissingOperand(String op, SourcePos pos) {
    opSet.reporter.report(new OperatorError.MissingOperand(pos, op));
  }

  @Override protected @Nullable OpDecl underlyingOpDecl(@NotNull Expr.NamedArg elem) {
    var expr = elem.term().data();
    while (expr instanceof Expr.Lift lift) expr = lift.expr().data();
    return expr instanceof Expr.Ref(var ref, _) ? resolveInfo.resolveOpDecl(ref) : null;
  }

  @Override protected @NotNull Expr.NamedArg
  makeArg(@NotNull SourcePos pos, @NotNull WithPos<Expr> func, Expr.@NotNull NamedArg arg, boolean explicit) {
    if (func.data() instanceof Expr.App app) {
      var newApp = new Expr.App(app.function(), app.argument().appended(new Expr.NamedArg(arg.explicit(), arg.term())));
      return new Expr.NamedArg(explicit, new WithPos<>(pos, newApp));
    } else {
      return new Expr.NamedArg(explicit, new WithPos<>(pos, new Expr.App(func, ImmutableSeq.of(arg))));
    }
  }

  @Override public @NotNull Expr.NamedArg makeSectionApp(
    @NotNull SourcePos pos, Expr.@NotNull NamedArg op, @NotNull Function<Expr.NamedArg, WithPos<Expr>> lamBody
  ) {
    var missing = Constants.randomlyNamed(op.term().sourcePos());
    var missingElem = new Expr.NamedArg(true, new WithPos<>(SourcePos.NONE, new Expr.Ref(missing)));
    var missingParam = new Expr.Param(missing.definition(), missing, true);
    var term = new Expr.Lambda(missingParam, lamBody.apply(missingElem));
    return new Expr.NamedArg(op.explicit(), new WithPos<>(pos, term));
  }
}
