// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Tuple;
import org.aya.compiler.morphism.Constants;
import org.aya.compiler.morphism.FreeJavaResolver;
import org.aya.compiler.morphism.JavaUtil;
import org.aya.compiler.morphism.ir.IrClassBuilder;
import org.aya.compiler.morphism.ir.IrCodeBuilder;
import org.aya.compiler.morphism.ir.IrExpr;
import org.aya.compiler.morphism.ir.IrValue;
import org.aya.states.primitive.ShapeFactory;
import org.aya.syntax.compile.JitCon;
import org.aya.syntax.compile.JitData;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.DataDef;
import org.aya.syntax.core.def.TyckAnyDef;
import org.aya.syntax.core.repr.AyaShape;
import org.aya.syntax.core.repr.CodeShape;
import org.aya.syntax.core.term.call.DataCall;
import org.aya.syntax.core.term.repr.IntegerTerm;
import org.aya.syntax.ref.DefVar;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;

// You should compile this with its constructors
public final class DataSerializer extends JitTeleSerializer<DataDef> {
  private final @NotNull ShapeFactory shapeFactory;

  public DataSerializer(@NotNull ShapeFactory shapeFactory, ModuleSerializer.@NotNull MatchyRecorder recorder) {
    super(JitData.class, recorder);
    this.shapeFactory = shapeFactory;
  }

  @Override public @NotNull DataSerializer serialize(@NotNull IrClassBuilder topBuilder, DataDef unit) {
    buildFramework(topBuilder, unit, builder -> {
      builder.buildMethod(
        JavaUtil.fromClass(JitCon.class).arrayType(), "constructors", false,
        ImmutableSeq.empty(), (_, cb) ->
          buildConstructors(cb, unit));

      var anyDef = AnyDef.fromVar(unit.ref());
      var maybe = shapeFactory.find(anyDef);
      if (maybe.isDefined()) {
        var recognition = maybe.get();
        if (recognition.shape() == AyaShape.NAT_SHAPE) builder.buildMethod(
          Constants.CD_IntegerTerm, AyaSerializer.METHOD_MAKE_INTEGER, true, ImmutableSeq.of(ConstantDescs.CD_int),
          (ap, codeBuilder) -> {
            var ourCall = codeBuilder.bindExpr(new IrExpr.RefField(FreeJavaResolver.resolve(
              NameSerializer.getClassDesc(anyDef),
              AyaSerializer.FIELD_EMPTYCALL,
              JavaUtil.fromClass(DataCall.class)), null));

            codeBuilder.returnWith(codeBuilder.mkNew(IntegerTerm.class, ImmutableSeq.of(
              ap.arg(0),
              AbstractExprSerializer.getInstance(codeBuilder, recognition.getCon(CodeShape.GlobalId.ZERO)),
              AbstractExprSerializer.getInstance(codeBuilder, recognition.getCon(CodeShape.GlobalId.SUC)),
              ourCall
            )));
          });
      }
    });

    return this;
  }

  @Override protected @NotNull Class<?> callClass() { return DataCall.class; }
  @Override protected int buildShape(DataDef unit) {
    var maybe = shapeFactory.find(TyckAnyDef.make(unit));
    if (maybe.isEmpty()) {
      return super.buildShape(unit);
    }

    return maybe.get().shape().ordinal();
  }

  @Override
  protected CodeShape.GlobalId[] buildRecognition(DataDef unit) {
    var maybe = shapeFactory.find(TyckAnyDef.make(unit));
    if (maybe.isEmpty()) {
      return super.buildRecognition(unit);
    }

    var recog = maybe.get();
    // The capture is one-to-one
    var flipped = ImmutableMap.from(recog.captures().view()
      .map((k, v) -> Tuple.<DefVar<?, ?>, CodeShape.GlobalId>of(((TyckAnyDef<?>) v).ref, k)));
    var capture = unit.body().map(x -> flipped.get(x.ref));
    return capture.toArray(CodeShape.GlobalId.class);
  }

  @Override
  protected @NotNull ImmutableSeq<ClassDesc> superConParams() {
    return super.superConParams().appended(ConstantDescs.CD_int);
  }

  @Override
  protected @NotNull ImmutableSeq<IrValue> superConArgs(@NotNull IrCodeBuilder builder, DataDef unit) {
    return super.superConArgs(builder, unit).appended(new IrExpr.Iconst(unit.body().size()));
  }

  /**
   * @see JitData#constructors()
   */
  private void buildConstructors(@NotNull IrCodeBuilder builder, DataDef unit) {
    var cons = Constants.JITDATA_CONS;
    var consRef = builder.bindExpr(cons.returnType(), new IrExpr.RefField(cons, IrExpr.This.INSTANCE));

    if (unit.body().isEmpty()) {
      builder.returnWith(consRef);
      return;
    }

    builder.ifNull(new IrExpr.GetArray(consRef, 0), cb ->
      unit.body().forEachIndexed((idx, con) ->
        cb.updateArray(consRef, idx,
          AbstractExprSerializer.getInstance(builder, con))), null);

    builder.returnWith(consRef);
  }
}
