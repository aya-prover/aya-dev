// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.FreezableMutableList;
import kala.collection.mutable.MutableList;
import kala.control.Either;
import org.aya.compiler.free.Constants;
import org.aya.compiler.free.FreeCodeBuilder;
import org.aya.compiler.free.FreeJavaExpr;
import org.aya.compiler.free.FreeUtil;
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
import java.util.function.Supplier;

import static org.aya.compiler.AyaSerializer.*;

public final class FnSerializer extends JitTeleSerializer<FnDef> {
  public static final String TYPE_STUCK = CLASS_SUPPLIER + "<" + CLASS_TERM + ">";

  private final @NotNull ShapeFactory shapeFactory;
  public FnSerializer(@NotNull ShapeFactory shapeFactory) {
    super(JitFn.class);
    this.shapeFactory = shapeFactory;
  }

  public static int modifierFlags(@NotNull EnumSet<Modifier> modies) {
    var flag = 0;

    for (var mody : modies) {
      flag |= 1 << mody.ordinal();
    }

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
    @NotNull FreeJavaExpr onStuckTerm,
    @NotNull ImmutableSeq<FreeJavaExpr> argTerms
  ) {
    Consumer<SourceBuilder> onStuckCon = s -> s.buildReturn(onStuckTerm + ".get()");

    if (unit.is(Modifier.Opaque)) {
      onStuckCon.accept(this);
      return;
    }

    switch (unit.body()) {
      case Either.Left(var expr) -> buildReturn(serializeTermUnderTele(expr, argTerms));
      case Either.Right(var clauses) -> {
        var ser = new PatternSerializer(this.sourceBuilder, argTerms, onStuckCon, onStuckCon);
        ser.serialize(null, clauses.view()
          .map(WithPos::data)
          .map(matching -> new PatternSerializer.Matching(
            matching.bindCount(), matching.patterns(), (s, bindSize) ->
            s.buildReturn(serializeTermUnderTele(matching.body(), PatternSerializer.VARIABLE_RESULT, bindSize))
          ))
          .toImmutableSeq());
      }
    }
  }

  /**
   * Build vararg `invoke`
   */
  private void buildInvoke(FnDef unit, @NotNull String onStuckTerm, @NotNull String argsTerm) {
    var teleSize = unit.telescope().size();

    buildReturn(SourceBuilder.fromSeq(argsTerm, teleSize).view()
      .prepended(onStuckTerm)
      .joinToString(", ", "this.invoke(", ")"));
  }

  @Override protected @NotNull Class<?> callClass() { return FnCall.class; }
  @Override protected void buildShape(FnDef unit) {
    var maybe = shapeFactory.find(TyckAnyDef.make(unit));
    if (maybe.isEmpty()) {
      super.buildShape(unit);
    } else {
      var recog = maybe.get();
      appendMetadataRecord("shape", Integer.toString(recog.shape().ordinal()), false);
      appendMetadataRecord("recognition", ExprializeUtils.makeHalfArrayFrom(Seq.empty()), false);
    }
  }

  @Override public FnSerializer serialize(@NotNull FreeCodeBuilder builder, FnDef unit) {
    var argsTerm = "args";
    var onStuckTerm = "onStuck";
    var onStuckParam = FreeUtil.fromClass(Supplier.class);
    var fullParam = FreezableMutableList.<ClassDesc>create();
    fullParam.append(onStuckParam);
    fullParam.appendAll(ImmutableSeq.fill(unit.telescope().size(), Constants.CD_Term));

    buildFramework(builder, unit, builder0 -> {
      builder0.buildMethod(
        Constants.CD_Term,
        "invoke",
        fullParam.freeze(),
        (ap, cb) -> {
          var onStuck = ap.arg(0);
          var args = ImmutableSeq.fill(unit.telescope().size(), i -> ap.arg(i + 1));
          buildInvoke(cb, unit, onStuck, args);
        }
      );

      builder0.buildMethod(
        Constants.CD_Term,
        "invoke",
        ImmutableSeq.of(onStuckParam, Constants.CD_Seq),
        (ap, cb) -> {

        }
      );
    });

    return this;
  }
}
