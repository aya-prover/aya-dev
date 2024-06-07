// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Tuple;
import org.aya.primitive.ShapeFactory;
import org.aya.syntax.compile.JitData;
import org.aya.syntax.core.def.DataDef;
import org.aya.syntax.core.def.TyckAnyDef;
import org.aya.syntax.core.repr.CodeShape;
import org.aya.syntax.ref.DefVar;
import org.jetbrains.annotations.NotNull;

import static org.aya.compiler.AyaSerializer.CLASS_DATACALL;
import static org.aya.compiler.NameSerializer.getClassReference;

// You should compile this with its constructors
public final class DataSerializer extends JitTeleSerializer<DataDef> {
  private final @NotNull ShapeFactory shapeFactory;

  public DataSerializer(@NotNull SourceBuilder builder, @NotNull ShapeFactory shapeFactory) {
    super(builder, JitData.class);
    this.shapeFactory = shapeFactory;
  }

  @Override public DataSerializer serialize(DataDef unit) {
    buildFramework(unit, () -> buildMethod("constructors", ImmutableSeq.empty(),
      STR."\{CLASS_JITCON}[]", true,
      () -> buildConstructors(unit)));
    return this;
  }

  @Override protected @NotNull String callClass() { return CLASS_DATACALL; }
  @Override protected void buildShape(DataDef unit) {
    var maybe = shapeFactory.find(TyckAnyDef.make(unit));
    if (maybe.isEmpty()) {
      super.buildShape(unit);
      return;
    }
    var recog = maybe.get();
    appendMetadataRecord("shape", Integer.toString(recog.shape().ordinal()), false);

    // The capture is one-to-one
    var flipped = ImmutableMap.from(recog.captures().view()
      .map((k, v) -> Tuple.<DefVar<?, ?>, CodeShape.GlobalId>of(((TyckAnyDef<?>) v).ref, k)));
    var capture = unit.body.map(x -> ExprializeUtils.makeSub(CLASS_GLOBALID, flipped.get(x.ref).toString()));
    appendMetadataRecord("recognition", ExprializeUtils.makeHalfArrayFrom(capture), false);
  }

  @Override protected void buildConstructor(DataDef unit) {
    buildConstructor(unit, ImmutableSeq.of(Integer.toString(unit.body.size())));
  }

  /**
   * @see JitData#constructors()
   */
  private void buildConstructors(DataDef unit) {
    var cRef = "this.constructors";

    buildIf(ExprializeUtils.isNull(STR."\{cRef}[0]"), () ->
      unit.body.forEachIndexed((idx, con) ->
        buildUpdate(STR."\{cRef}[\{idx}]", ExprializeUtils.getInstance(getClassReference(con.ref)))));

    buildReturn(cRef);
  }
}
