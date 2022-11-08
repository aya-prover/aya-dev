// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.desugar;

import kala.collection.SeqView;
import kala.value.MutableValue;
import org.aya.concrete.Pattern;
import org.aya.concrete.error.OperatorError;
import org.aya.generic.Arg;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.Context;
import org.aya.tyck.pat.PatternProblem;
import org.aya.util.binop.Assoc;
import org.aya.util.binop.BinOpParser;
import org.aya.util.binop.BinOpSet;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public final class BinPatternParser extends BinOpParser<AyaBinOpSet, Pattern, Pattern.PatElem> {
  private final @NotNull ResolveInfo resolveInfo;
  private final @Nullable LocalVar myAs;

  public BinPatternParser(@NotNull ResolveInfo resolveInfo, @NotNull SeqView<Pattern.@NotNull PatElem> seq, @Nullable LocalVar as) {
    super(resolveInfo.opSet(), seq);
    this.resolveInfo = resolveInfo;
    this.myAs = as;
  }

  @Override protected @NotNull BinOpParser<AyaBinOpSet, Pattern, Pattern.PatElem>
  replicate(@NotNull SeqView<Pattern.@NotNull PatElem> seq) {
    return new BinPatternParser(resolveInfo, seq, myAs);
  }

  private static final Pattern.PatElem OP_APP = new Pattern.PatElem(true, new Pattern.Bind(
    SourcePos.NONE,
    new LocalVar(BinOpSet.APP_ELEM.name()),
    MutableValue.create()));

  @Override protected @NotNull Pattern.PatElem appOp() {
    return OP_APP;
  }

  @Override public @NotNull Pattern.PatElem
  makeSectionApp(@NotNull SourcePos pos, Pattern.@NotNull PatElem op, @NotNull Function<Pattern.PatElem, Pattern> lamBody) {
    return new Pattern.PatElem(op.explicit(), createErrorExpr(pos));
  }

  @Override protected void reportAmbiguousPred(String op1, String op2, SourcePos pos) {
    opSet.reporter.report(new OperatorError.Precedence(op1, op2, pos));
  }

  @Override protected void reportFixityError(Assoc top, Assoc current, String topOp, String currentOp, SourcePos pos) {
    opSet.reporter.report(new OperatorError.Fixity(currentOp, current, topOp, top, pos));
  }

  @Override protected @NotNull Pattern createErrorExpr(@NotNull SourcePos sourcePos) {
    return new Pattern.Bind(sourcePos, new LocalVar("a broken constructor pattern"), MutableValue.create());
  }

  @Override protected @Nullable OpDecl underlyingOpDecl(@NotNull Pattern.PatElem elem) {
    return elem.expr() instanceof Pattern.Ctor ref && ref.resolved().data() instanceof DefVar<?, ?> defVar
      ? defVar.resolveOpDecl(resolveInfo.thisModule().moduleName())
      : null;
  }

  @Override protected @NotNull Pattern.PatElem
  makeArg(@NotNull SourcePos pos, @NotNull Pattern func, Pattern.@NotNull PatElem arg, boolean explicit) {
    // param explicit should be ignored since the BinOpSeq we are processing already specified the explicitness
    if (func instanceof Pattern.Ctor ctor) {
      var newCtor = new Pattern.Ctor(pos, ctor.resolved(), ctor.params().appended(new Arg<>(arg.expr(), arg.explicit())), myAs);
      return new Pattern.PatElem(explicit, newCtor);
    } else {
      opSet.reporter.report(new PatternProblem.UnknownCtor(func));
      throw new Context.ResolvingInterruptedException();
    }
  }
}
