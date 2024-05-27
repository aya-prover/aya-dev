// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Tuple;
import org.aya.generic.NameGenerator;
import org.aya.primitive.ShapeFactory;
import org.aya.syntax.compile.JitData;
import org.aya.syntax.core.def.DataDef;
import org.aya.syntax.core.def.TyckAnyDef;
import org.aya.syntax.core.repr.CodeShape;
import org.aya.syntax.ref.DefVar;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

// You should compile this with its constructors
public final class DataSerializer extends JitTeleSerializer<DataDef> {
  private final @NotNull Consumer<DataSerializer> conContinuation;
  private final @NotNull ShapeFactory shapeFactory;

  /**
   * @param conContinuation should generate constructor inside of this data
   */
  public DataSerializer(
    @NotNull StringBuilder builder,
    int indent,
    @NotNull NameGenerator nameGen,
    @NotNull ShapeFactory shapeFactory,
    @NotNull Consumer<DataSerializer> conContinuation
  ) {
    super(builder, indent, nameGen, JitData.class);
    this.shapeFactory = shapeFactory;
    this.conContinuation = conContinuation;
  }

  public DataSerializer(
    @NotNull AbstractSerializer<?> other,
    @NotNull ShapeFactory shapeFactory,
    @NotNull Consumer<DataSerializer> conContinuation
  ) {
    super(other, JitData.class);
    this.shapeFactory = shapeFactory;
    this.conContinuation = conContinuation;
  }

  @Override public AyaSerializer<DataDef> serialize(DataDef unit) {
    buildFramework(unit, () -> {
      buildMethod("constructors", ImmutableSeq.empty(), STR."\{CLASS_JITCON}[]", true,
        () -> buildConstructors(unit));
      appendLine();
      conContinuation.accept(this);
    });

    return this;
  }

  @Override
  protected void buildShape(DataDef unit) {
    var maybe = shapeFactory.find(TyckAnyDef.make(unit));
    if (maybe.isEmpty()) {
      super.buildShape(unit);
    } else {
      var recog = maybe.get();
      appendMetadataRecord("shape", Integer.toString(recog.shape().ordinal()), false);

      // The capture is one-to-one
      var flipped = ImmutableMap.from(recog.captures().view()
        .map((k, v) -> Tuple.<DefVar<?, ?>, CodeShape.GlobalId>of(((TyckAnyDef<?>) v).ref, k)));
      var capture = unit.body.map(x -> makeSub(CLASS_GLOBALID, flipped.get(x.ref).toString()));
      appendMetadataRecord("recognition", makeHalfArrayFrom(capture), false);
    }
  }

  @Override protected void buildConstructor(DataDef unit) {
    buildConstructor(unit, ImmutableSeq.of(Integer.toString(unit.body.size())));
  }

  /**
   * @see JitData#constructors()
   */
  private void buildConstructors(DataDef unit) {
    var cRef = "this.constructors";

    buildIf(isNull(STR."\{cRef}[0]"), () ->
      unit.body.forEachIndexed((idx, con) ->
        buildUpdate(STR."\{cRef}[\{idx}]", getInstance(getCoreReference(con.ref)))));

    buildReturn(cRef);
  }
}
