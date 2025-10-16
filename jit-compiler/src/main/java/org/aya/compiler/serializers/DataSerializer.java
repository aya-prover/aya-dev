// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Tuple;
import org.aya.compiler.morphism.AstUtil;
import org.aya.compiler.morphism.Constants;
import org.aya.compiler.morphism.ast.AstClassBuilder;
import org.aya.compiler.morphism.ast.AstCodeBuilder;
import org.aya.compiler.morphism.ast.AstExpr;
import org.aya.compiler.morphism.ast.AstVariable;
import org.aya.primitive.ShapeFactory;
import org.aya.syntax.compile.JitCon;
import org.aya.syntax.compile.JitData;
import org.aya.syntax.core.def.DataDef;
import org.aya.syntax.core.def.TyckAnyDef;
import org.aya.syntax.core.repr.CodeShape;
import org.aya.syntax.core.term.call.DataCall;
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

  @Override public @NotNull DataSerializer serialize(@NotNull AstClassBuilder builder, DataDef unit) {
    buildFramework(builder, unit, builder0 -> builder0.buildMethod(
      AstUtil.fromClass(JitCon.class).arrayType(),
      "constructors",
      ImmutableSeq.empty(), (_, cb) ->
        buildConstructors(cb, unit)));

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
  protected @NotNull ImmutableSeq<AstVariable> superConArgs(@NotNull AstCodeBuilder builder, DataDef unit) {
    return super.superConArgs(builder, unit).appended(builder.iconst(unit.body().size()));
  }

  /**
   * @see JitData#constructors()
   */
  private void buildConstructors(@NotNull AstCodeBuilder builder, DataDef unit) {
    var cons = Constants.JITDATA_CONS;
    var consRef = new AstExpr.RefField(cons, builder.thisRef());

    if (unit.body().isEmpty()) {
      builder.returnWith(consRef);
      return;
    }

    builder.ifNull(builder.getArray(consRef, 0), cb ->
      unit.body().forEachIndexed((idx, con) ->
        cb.updateArray(consRef, idx,
          new AstExpr.Ref(AbstractExprializer.getInstance(builder, con)))), null);

    builder.returnWith(consRef);
  }
}
