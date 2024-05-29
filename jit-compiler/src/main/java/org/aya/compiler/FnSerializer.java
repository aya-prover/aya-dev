// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.control.Either;
import org.aya.generic.Modifier;
import org.aya.generic.NameGenerator;
import org.aya.primitive.ShapeFactory;
import org.aya.syntax.compile.JitFn;
import org.aya.syntax.core.def.FnDef;
import org.aya.syntax.core.def.TyckAnyDef;
import org.jetbrains.annotations.NotNull;

public final class FnSerializer extends JitTeleSerializer<FnDef> {
  private final @NotNull ShapeFactory shapeFactory;
  public FnSerializer(@NotNull StringBuilder builder, int indent, @NotNull NameGenerator nameGen, @NotNull ShapeFactory shapeFactory) {
    super(builder, indent, nameGen, JitFn.class);
    this.shapeFactory = shapeFactory;
  }

  public FnSerializer(@NotNull AbstractSerializer<?> other, @NotNull ShapeFactory shapeFactory) {
    super(other, JitFn.class);
    this.shapeFactory = shapeFactory;
  }

  @Override protected void buildConstructor(FnDef unit) {
    super.buildConstructor(unit, ImmutableSeq.empty());
  }

  /**
   * Build fixed argument `invoke`
   */
  private void buildInvoke(FnDef unit, @NotNull String onStuckTerm, @NotNull ImmutableSeq<String> argTerms) {
    if (unit.is(Modifier.Opaque)) {
      buildReturn(onStuckTerm);
      return;
    }

    switch (unit.body()) {
      case Either.Left(var expr) -> buildReturn(serializeTermUnderTele(expr, argTerms));
      case Either.Right(var clauses) -> {
        var ser = new PatternSerializer(this.builder, this.indent, this.nameGen, argTerms, false,
          s -> s.buildReturn(onStuckTerm), s -> s.buildReturn(onStuckTerm));
        ser.serialize(clauses.map(matching -> new PatternSerializer.Matching(
          matching.patterns(),
          (s, bindSize) -> s.buildReturn(serializeTermUnderTele(matching.body(), PatternSerializer.VARIABLE_RESULT, bindSize))
        )));
      }
    }
  }

  /**
   * Build vararg `invoke`
   */
  private void buildInvoke(FnDef unit, @NotNull String onStuckTerm, @NotNull String argsTerm) {
    var teleSize = unit.telescope().size();

    buildReturn(fromSeq(argsTerm, teleSize).view()
      .prepended(onStuckTerm)
      .joinToString(", ", "this.invoke(", ")"));
  }
  @Override protected void buildShape(FnDef unit) {
    var maybe = shapeFactory.find(TyckAnyDef.make(unit));
    if (maybe.isEmpty()) {
      super.buildShape(unit);
    } else {
      var recog = maybe.get();
      appendMetadataRecord("shape", Integer.toString(recog.shape().ordinal()), false);
      appendMetadataRecord("recognition", makeHalfArrayFrom(Seq.empty()), false);
    }
  }
  @Override public AyaSerializer<FnDef> serialize(FnDef unit) {
    var argsTerm = "args";
    var onStuckTerm = "onStuck";
    var onStuckParam = new JitParam(onStuckTerm, CLASS_TERM);
    var names = ImmutableSeq.fill(unit.telescope().size(), () -> nameGen.nextName(null));
    var fixedParams = MutableList.<JitParam>create();
    fixedParams.append(onStuckParam);
    fixedParams.appendAll(names.view().map(x -> new JitParam(x, CLASS_TERM)));

    buildFramework(unit, () -> {
      buildMethod("invoke", fixedParams.toImmutableSeq(),
        CLASS_TERM, false, () -> buildInvoke(unit, onStuckTerm, names));
      appendLine();
      buildMethod("invoke", ImmutableSeq.of(onStuckParam, new JitParam(argsTerm, TYPE_TERMSEQ)),
        CLASS_TERM, true, () -> buildInvoke(unit, onStuckTerm, argsTerm));
    });

    return this;
  }
}
