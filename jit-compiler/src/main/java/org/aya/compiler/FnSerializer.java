// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.control.Either;
import org.aya.generic.Modifier;
import org.aya.generic.NameGenerator;
import org.aya.syntax.compile.JitFn;
import org.aya.syntax.core.def.FnDef;
import org.jetbrains.annotations.NotNull;

public final class FnSerializer extends JitTeleSerializer<FnDef> {
  public FnSerializer(@NotNull StringBuilder builder, int indent, @NotNull NameGenerator nameGen) {
    super(builder, indent, nameGen, JitFn.class);
  }

  public FnSerializer(@NotNull AbstractSerializer<?> other) {
    super(other, JitFn.class);
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

    buildReturn(STR."this.invoke(\{fromSeq(argsTerm, teleSize).view()
      .prepended(onStuckTerm)
      .joinToString()})");
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
