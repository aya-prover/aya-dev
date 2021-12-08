// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.desugar;

import kala.collection.SeqView;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.concrete.Pattern;
import org.aya.concrete.error.OperatorProblem;
import org.aya.concrete.resolve.context.Context;
import org.aya.tyck.pat.PatternProblem;
import org.aya.util.binop.Assoc;
import org.aya.util.binop.BinOpParser;
import org.aya.util.binop.BinOpSet;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public final class BinPatternParser extends BinOpParser<AyaBinOpSet, Pattern, Pattern> {
  private final boolean outerMostLicit;

  public BinPatternParser(boolean outerMostLicit, @NotNull AyaBinOpSet opSet, @NotNull SeqView<@NotNull Pattern> seq) {
    super(opSet, seq);
    this.outerMostLicit = outerMostLicit;
  }

  @Override protected @NotNull BinOpParser<AyaBinOpSet, Pattern, Pattern>
  replicate(@NotNull SeqView<@NotNull Pattern> seq) {
    return new BinPatternParser(outerMostLicit, opSet, seq);
  }

  private static final Pattern OP_APP = new Pattern.Bind(
    SourcePos.NONE, true,
    new LocalVar(BinOpSet.APP_ELEM.name()));

  @Override protected @NotNull Pattern appOp() {
    return OP_APP;
  }

  @Override public @NotNull Pattern
  makeSectionApp(@NotNull SourcePos pos, @NotNull Pattern op, @NotNull Function<Pattern, Pattern> lamBody) {
    return createErrorExpr(pos);
  }

  @Override protected void reportAmbiguousPred(String op1, String op2, SourcePos pos) {
    opSet.reporter.report(new OperatorProblem.AmbiguousPredError(op1, op2, pos));
  }

  @Override protected void reportFixityError(Assoc top, Assoc current, String topOp, String currentOp, SourcePos pos) {
    opSet.reporter.report(new OperatorProblem.FixityError(currentOp, current, topOp, top, pos));
  }

  @Override protected @NotNull Pattern createErrorExpr(@NotNull SourcePos sourcePos) {
    return new Pattern.Bind(sourcePos, true, new LocalVar("a broken constructor pattern"));
  }

  @Override protected @Nullable OpDecl underlyingOpDecl(@NotNull Pattern elem) {
    return elem.expr() instanceof Pattern.Ctor ref && ref.resolved().data() instanceof DefVar<?, ?> defVar
      ? defVar.opDecl
      : null;
  }

  @Override protected @NotNull Pattern
  makeArg(@NotNull SourcePos pos, @NotNull Pattern func, @NotNull Pattern arg, boolean explicit) {
    // param explicit should be ignored since the BinOpSeq we are processing already specified the explicitness
    if (func instanceof Pattern.Ctor ctor) {
      return new Pattern.Ctor(pos, outerMostLicit, ctor.resolved(), ctor.params().appended(arg), ctor.as());
    } else {
      opSet.reporter.report(new PatternProblem.UnknownCtor(func));
      throw new Context.ResolvingInterruptedException();
    }
  }
}
