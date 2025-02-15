// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import org.aya.compiler.LocalVariable;
import org.aya.compiler.MethodRef;
import org.aya.compiler.morphism.ClassBuilder;
import org.aya.compiler.morphism.CodeBuilder;
import org.aya.compiler.morphism.Constants;
import org.aya.compiler.morphism.JavaExpr;
import org.aya.generic.Modifier;
import org.aya.primitive.ShapeFactory;
import org.aya.syntax.compile.JitFn;
import org.aya.syntax.core.def.FnDef;
import org.aya.syntax.core.def.TyckAnyDef;
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

  /// @see JitFn#invoke(java.util.function.UnaryOperator, Seq)
  public static @NotNull MethodRef resolveInvoke(@NotNull ClassDesc owner, int argc) {
    return new MethodRef(
      owner, "invoke", Constants.CD_Term, ImmutableSeq.fill(1 + argc, i ->
      i == 0
        ? Constants.CD_UnaryOperator
        : Constants.CD_Term
    ), false);
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

  /**
   * Build fixed argument `invoke`
   */
  private void buildInvoke(
    @NotNull CodeBuilder builder,
    @NotNull FnDef unit,
    @NotNull LocalVariable preTerm,
    @NotNull ImmutableSeq<LocalVariable> argTerms
  ) {
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
          var result = serializerContext.serializeTermUnderTele(builder0, matching.body(), patSer.result.view()
              .take(count)
              .map(LocalVariable::ref)
              .toSeq());
            builder0.returnWith(result);
          })
        ).toSeq());
      }
    }
  }

  /**
   * Build vararg `invoke`
   */
  private void buildInvoke(
    @NotNull CodeBuilder builder,
    @NotNull FnDef unit,
    @NotNull MethodRef invokeMethod,
    @NotNull LocalVariable preTerm,
    @NotNull LocalVariable argsTerm
  ) {
    var teleSize = unit.telescope().size();
    var args = AbstractExprializer.fromSeq(builder, Constants.CD_Term, argsTerm.ref(), teleSize);
    var result = builder.invoke(invokeMethod, builder.thisRef(), args.prepended(preTerm.ref()));
    builder.returnWith(result);
  }

  @Override protected @NotNull Class<?> callClass() { return FnCall.class; }

  @Override protected int buildShape(FnDef unit) {
    var shapeMaybe = shapeFactory.find(TyckAnyDef.make(unit));
    if (shapeMaybe.isEmpty()) return super.buildShape(unit);
    return shapeMaybe.get().shape().ordinal();
  }

  @Override public @NotNull FnSerializer serialize(@NotNull ClassBuilder builder, FnDef unit) {
    var fullParam = ImmutableSeq.fill(1 + unit.telescope().size(), i ->
      i == 0
        ? Constants.CD_UnaryOperator
        : Constants.CD_Term
    );

    buildFramework(builder, unit, builder0 -> {
      var fixedInvoke = builder0.buildMethod(
        Constants.CD_Term,
        "invoke",
        fullParam,
        (ap, cb) -> {
          var pre = ap.arg(0);
          var args = ImmutableSeq.fill(unit.telescope().size(),
            i -> ap.arg(i + 1));
          buildInvoke(cb, unit, pre, args);
        }
      );

      builder0.buildMethod(
        Constants.CD_Term,
        "invoke",
        ImmutableSeq.of(Constants.CD_UnaryOperator, Constants.CD_Seq),
        (ap, cb) ->
          buildInvoke(cb, unit, fixedInvoke, ap.arg(0), ap.arg(1))
      );
    });

    return this;
  }
}
