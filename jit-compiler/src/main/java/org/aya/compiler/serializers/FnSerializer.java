// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import org.aya.compiler.LocalVariable;
import org.aya.compiler.MethodRef;
import org.aya.compiler.morphism.*;
import org.aya.generic.Modifier;
import org.aya.primitive.ShapeFactory;
import org.aya.syntax.compile.JitFn;
import org.aya.syntax.core.def.FnDef;
import org.aya.syntax.core.def.TyckAnyDef;
import org.aya.syntax.core.term.LetTerm;
import org.aya.syntax.core.term.call.FnCall;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.util.EnumSet;
import java.util.function.Consumer;

public final class FnSerializer extends JitTeleSerializer<FnDef> {
  private final @NotNull ShapeFactory shapeFactory;

  public FnSerializer(@NotNull ShapeFactory shapeFactory, ModuleSerializer.@NotNull MatchyRecorder recorder) {
    super(JitFn.class, recorder);
    this.shapeFactory = shapeFactory;
  }

  public static int modifierFlags(@NotNull EnumSet<Modifier> modies) {
    var flag = 0;
    for (var mody : modies) flag |= 1 << mody.ordinal();
    return flag;
  }

  @Override
  protected @NotNull ImmutableSeq<ClassDesc> superConParams() {
    return super.superConParams().appended(ConstantDescs.CD_int);
  }

  @Override
  protected @NotNull ImmutableSeq<JavaExpr> superConArgs(@NotNull CodeBuilder builder, FnDef unit) {
    return super.superConArgs(builder, unit)
      .appended(builder.iconst(modifierFlags(unit.modifiers())));
  }

  public static @NotNull JavaExpr makeInvoke(
    @NotNull ExprBuilder builder,
    @NotNull ClassDesc owner,
    @NotNull JavaExpr normalizer,
    @NotNull ImmutableSeq<JavaExpr> args
  ) {
    var ref = new MethodRef(
      owner, "invoke", Constants.CD_Term,
      InvokeSignatureHelper.parameters(ImmutableSeq.fill(args.size(), Constants.CD_Term).view()),
      false
    );

    var instance = TermExprializer.getInstance(builder, owner);
    return AbstractExprializer.makeCallInvoke(builder, ref, instance, normalizer, args.view());
  }

  /**
   * Build fixed argument `invoke`
   */
  private void buildInvoke(
    @NotNull CodeBuilder topBuilder,
    @NotNull FnDef unit,
    @NotNull LocalVariable preTerm,
    @NotNull ImmutableSeq<LocalVariable> argTerms
  ) {
    Consumer<CodeBuilder> buildFn = builder -> {
      Consumer<CodeBuilder> onStuckCon = cb -> {
        var stuckTerm = TermExprializer.buildFnCall(cb, FnCall.class, unit, 0, argTerms.map(LocalVariable::ref));
        cb.returnWith(stuckTerm);
      };

      var argExprs = argTerms.map(LocalVariable::ref);
      var normalizer = preTerm.ref();
      var serializerContext = buildSerializerContext(normalizer);

      if (unit.is(Modifier.Opaque)) {
        onStuckCon.accept(builder);
        return;
      }

      switch (unit.body()) {
        case Either.Left(var expr) -> {
          var result = serializerContext.serializeTermUnderTele(builder, expr, argExprs);
          builder.returnWith(result);
        }
        case Either.Right(var clauses) -> {
          var ser = new PatternSerializer(argExprs, onStuckCon, serializerContext, unit.is(Modifier.Overlap));
          ser.serialize(builder, clauses.matchingsView().map(matching -> new PatternSerializer.Matching(
              matching.bindCount(), matching.patterns(), (patSer, builder0, count) -> {
            if (LetTerm.makeAll(matching.body()) instanceof FnCall call && call.tailCall()) {
                var args = serializerContext.serializeTailCallUnderTele(builder0, call, patSer.result.view()
                  .take(count)
                  .map(LocalVariable::ref)
                  .toSeq());
                assert argTerms.size() == args.size();
                // Will cause conflict in theory, but won't in practice due to current local variable
                // declaration heuristics.
                argTerms.forEachWith(args, (a, b) -> {
                  builder0.updateVar(a, b);
                });
                builder0.continueLoop();
              } else {
                var result = serializerContext.serializeTermUnderTele(builder0, matching.body(), patSer.result.view()
                  .take(count)
                  .map(LocalVariable::ref)
                  .toSeq());
                builder0.returnWith(result);
              }
            })
          ).toSeq());
        }
      }
    };

    if (unit.modifiers().contains(Modifier.Tailrec)) {
      topBuilder.whileTrue(buildFn);
    } else {
      buildFn.accept(topBuilder);
    }
  }

  /**
   * Build vararg `invoke`
   */
  private void buildInvoke(
    @NotNull CodeBuilder builder,
    @NotNull FnDef unit,
    @NotNull MethodRef invokeMethod,
    @NotNull LocalVariable normalizerTerm,
    @NotNull LocalVariable argsTerm
  ) {
    var teleSize = unit.telescope().size();
    var args = AbstractExprializer.fromSeq(builder, Constants.CD_Term, argsTerm.ref(), teleSize);
    var result = AbstractExprializer.makeCallInvoke(builder, invokeMethod, builder.thisRef(), normalizerTerm.ref(), args.view());
    builder.returnWith(result);
  }

  @Override protected @NotNull Class<?> callClass() { return FnCall.class; }

  @Override protected int buildShape(FnDef unit) {
    var shapeMaybe = shapeFactory.find(TyckAnyDef.make(unit));
    if (shapeMaybe.isEmpty()) return super.buildShape(unit);
    return shapeMaybe.get().shape().ordinal();
  }

  @Override public @NotNull FnSerializer serialize(@NotNull ClassBuilder builder, FnDef unit) {
    buildFramework(builder, unit, builder0 -> {
      var fixedInvoke = builder0.buildMethod(
        Constants.CD_Term,
        "invoke",
        InvokeSignatureHelper.parameters(ImmutableSeq.fill(unit.telescope().size(), Constants.CD_Term).view()),
        (ap, cb) -> {
          var pre = InvokeSignatureHelper.normalizer(ap);
          var args = ImmutableSeq.fill(unit.telescope().size(),
            i -> InvokeSignatureHelper.arg(ap, i));
          buildInvoke(cb, unit, pre, args);
        }
      );

      builder0.buildMethod(
        Constants.CD_Term,
        "invoke",
        InvokeSignatureHelper.parameters(ImmutableSeq.of(Constants.CD_Seq).view()),
        (ap, cb) ->
          buildInvoke(cb, unit, fixedInvoke, InvokeSignatureHelper.normalizer(ap), InvokeSignatureHelper.arg(ap, 0))
      );
    });

    return this;
  }
}
