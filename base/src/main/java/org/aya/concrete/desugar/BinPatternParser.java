// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.desugar;

import kala.collection.SeqView;
import org.aya.concrete.Pattern;
import org.aya.concrete.error.OperatorError;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.Context;
import org.aya.tyck.pat.PatternProblem;
import org.aya.util.Arg;
import org.aya.util.binop.Assoc;
import org.aya.util.binop.BinOpParser;
import org.aya.util.binop.BinOpSet;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public final class BinPatternParser extends BinOpParser<AyaBinOpSet, Pattern, Arg<Pattern>> {
  private final @NotNull ResolveInfo resolveInfo;
  private final @Nullable LocalVar myAs;

  public BinPatternParser(@NotNull ResolveInfo resolveInfo, @NotNull SeqView<Arg<@NotNull Pattern>> seq, @Nullable LocalVar as) {
    super(resolveInfo.opSet(), seq);
    this.resolveInfo = resolveInfo;
    this.myAs = as;
  }

  @Override protected @NotNull BinOpParser<AyaBinOpSet, Pattern, Arg<Pattern>>
  replicate(@NotNull SeqView<Arg<@NotNull Pattern>> seq) {
    return new BinPatternParser(resolveInfo, seq, myAs);
  }

  private static final Arg<Pattern> OP_APP = new Arg<>(new Pattern.Bind(
    SourcePos.NONE,
    new LocalVar(BinOpSet.APP_ELEM.name())), true);

  @Override protected @NotNull Arg<Pattern> appOp() {
    return OP_APP;
  }

  @Override public @NotNull Arg<Pattern>
  makeSectionApp(@NotNull SourcePos pos, @NotNull Arg<@NotNull Pattern> op, @NotNull Function<Arg<Pattern>, Pattern> lamBody) {
    return new Arg<>(createErrorExpr(pos), op.explicit());
  }

  @Override protected void reportAmbiguousPred(String op1, String op2, SourcePos pos) {
    opSet.reporter.report(new OperatorError.Precedence(op1, op2, pos));
  }

  @Override protected void reportFixityError(Assoc top, Assoc current, String topOp, String currentOp, SourcePos pos) {
    opSet.reporter.report(new OperatorError.Fixity(currentOp, current, topOp, top, pos));
  }

  @Override protected void reportMissingOperand(String op, SourcePos pos) {
    opSet.reporter.report(new OperatorError.MissingOperand(pos, op));
  }

  @Override protected @NotNull Pattern createErrorExpr(@NotNull SourcePos sourcePos) {
    return new Pattern.Bind(sourcePos, new LocalVar("a broken constructor pattern"));
  }

  @Override protected @Nullable OpDecl underlyingOpDecl(@NotNull Arg<Pattern> elem) {
    return elem.term() instanceof Pattern.Ctor ref && ref.resolved().data() instanceof DefVar<?, ?> defVar
      ? defVar.resolveOpDecl(resolveInfo.thisModule().moduleName())
      : null;
  }

  @Override protected @NotNull Arg<Pattern>
  makeArg(@NotNull SourcePos pos, @NotNull Pattern func, @NotNull Arg<@NotNull Pattern> arg, boolean explicit) {
    // param explicit should be ignored since the BinOpSeq we are processing already specified the explicitness
    if (func instanceof Pattern.Ctor ctor) {
      var newCtor = new Pattern.Ctor(pos, ctor.resolved(), ctor.params().appended(new Arg<>(arg.term(), arg.explicit())), myAs);
      return new Arg<>(newCtor, explicit);
    } else {
      opSet.reporter.report(new PatternProblem.UnknownCtor(func));
      throw new Context.ResolvingInterruptedException();
    }
  }
}
