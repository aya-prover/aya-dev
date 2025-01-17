// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import org.aya.compiler.free.Constants;
import org.aya.compiler.free.FreeClassBuilder;
import org.aya.compiler.free.FreeCodeBuilder;
import org.aya.compiler.free.FreeJavaExpr;
import org.aya.compiler.free.data.MethodRef;
import org.aya.compiler.free.data.LocalVariable;
import org.aya.generic.Modifier;
import org.aya.primitive.ShapeFactory;
import org.aya.syntax.compile.JitFn;
import org.aya.syntax.core.def.FnDef;
import org.aya.syntax.core.def.TyckAnyDef;
import org.aya.syntax.core.term.call.FnCall;
import org.aya.util.error.WithPos;
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

  public static @NotNull MethodRef resolveInvoke(@NotNull ClassDesc owner, int argc) {
    return new MethodRef(
      owner, "invoke", Constants.CD_Term, ImmutableSeq.of(Constants.CD_Thunk)
      .appendedAll(ImmutableSeq.fill(argc, Constants.CD_Term)), false
    );
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
  protected @NotNull ImmutableSeq<FreeJavaExpr> superConArgs(@NotNull FreeCodeBuilder builder, FnDef unit) {
    return super.superConArgs(builder, unit)
      .appended(builder.iconst(modifierFlags(unit.modifiers())));
  }

  /**
   * Build fixed argument `invoke`
   */
  private void buildInvoke(
    @NotNull FreeCodeBuilder builder,
    @NotNull FnDef unit,
    @NotNull ImmutableSeq<LocalVariable> argTerms
  ) {
    Consumer<FreeCodeBuilder> onStuckCon = cb -> {
      var stuckTerm = TermExprializer.buildFnCall(cb, FnCall.class, unit, 0, argTerms.map(LocalVariable::ref));
      cb.returnWith(stuckTerm);
    };

    var argExprs = argTerms.map(LocalVariable::ref);

    if (unit.is(Modifier.Opaque)) {
      onStuckCon.accept(builder);
      return;
    }

    switch (unit.body()) {
      case Either.Left(var expr) -> {
        var result = serializeTermUnderTele(builder, expr, argExprs);
        builder.returnWith(result);
      }
      case Either.Right(var clauses) -> {
        var ser = new PatternSerializer(argExprs, onStuckCon, unit.is(Modifier.Overlap));
        ser.serialize(builder, clauses.view()
          .map(WithPos::data)
          .map(matching -> new PatternSerializer.Matching(
              matching.bindCount(), matching.patterns(), (patSer, builder0, bindSize) -> {
              var result = serializeTermUnderTele(
                builder0,
                matching.body(),
                patSer.result.ref(),
                bindSize
              );
              builder0.returnWith(result);
            })
          ).toImmutableSeq());
      }
    }
  }

  /**
   * Build vararg `invoke`
   */
  private void buildInvoke(@NotNull FreeCodeBuilder builder, @NotNull FnDef unit, @NotNull MethodRef invokeMethod, @NotNull LocalVariable argsTerm) {
    var teleSize = unit.telescope().size();
    var args = AbstractExprializer.fromSeq(builder, Constants.CD_Term, argsTerm.ref(), teleSize);
    var result = builder.invoke(invokeMethod, builder.thisRef(), args);
    builder.returnWith(result);
  }

  @Override protected @NotNull Class<?> callClass() { return FnCall.class; }

  @Override protected int buildShape(FnDef unit) {
    var shapeMaybe = shapeFactory.find(TyckAnyDef.make(unit));
    if (shapeMaybe.isEmpty()) return super.buildShape(unit);
    return shapeMaybe.get().shape().ordinal();
  }

  @Override public @NotNull FnSerializer serialize(@NotNull FreeClassBuilder builder, FnDef unit) {
    var fullParam = ImmutableSeq.fill(unit.telescope().size(), Constants.CD_Term);

    buildFramework(builder, unit, builder0 -> {
      var fixedInvoke = builder0.buildMethod(
        Constants.CD_Term,
        "invoke",
        fullParam,
        (ap, cb) -> {
          var args = ImmutableSeq.fill(unit.telescope().size(),
            ap::arg);
          buildInvoke(cb, unit, args);
        }
      );

      builder0.buildMethod(
        Constants.CD_Term,
        "invoke",
        ImmutableSeq.of(Constants.CD_Seq),
        (ap, cb) ->
          buildInvoke(cb, unit, fixedInvoke, ap.arg(0))
      );
    });

    return this;
  }
}
