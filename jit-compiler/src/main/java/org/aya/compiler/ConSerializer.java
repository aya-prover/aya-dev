// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.function.BooleanConsumer;
import org.aya.generic.NameGenerator;
import org.aya.syntax.compile.JitCon;
import org.aya.syntax.core.def.ConDef;
import org.aya.syntax.core.def.ConDefLike;
import org.jetbrains.annotations.NotNull;

public final class ConSerializer extends JitTeleSerializer<ConDef> {
  public ConSerializer(@NotNull StringBuilder builder, int indent, @NotNull NameGenerator nameGen) {
    super(builder, indent, nameGen, JitCon.class);
  }

  public ConSerializer(@NotNull AbstractSerializer<?> other) {
    super(other, JitCon.class);
  }

  @Override protected void buildConstructor(ConDef unit) {
    var hasEq = unit.equality != null;
    buildConstructor(unit, ImmutableSeq.of(getInstance(getCoreReference(unit.dataRef)), Boolean.toString(hasEq)));
  }

  private void buildIsAvailable(ConDef unit, @NotNull String argsTerm) {
    var pats = unit.pats;
    var names = buildGenLocalVarsFromSeq(CLASS_TERM, argsTerm, pats.size());
    appendLine();
    var ser = new PatternSerializer(this.builder, this.indent, this.nameGen, names, true,
      s -> s.buildReturn(STR."\{CLASS_RESULT}.err(true)"),
      s -> s.buildReturn(STR."\{CLASS_RESULT}.err(false)"));

    ser.serialize(ImmutableSeq.of(new PatternSerializer.Matching(pats,
      // we have only one clause, so the size is useless
      (s, _) -> s.buildReturn(STR."\{CLASS_RESULT}.ok(\{PatternSerializer.VARIABLE_RESULT}.toImmutableSeq())"))));
  }

  /**
   * @see ConDefLike#equality(Seq, boolean)
   */
  private void buildEquality(ConDef unit, @NotNull String argsTerm, @NotNull String is0Term) {
    var eq = unit.equality;
    if (eq == null) {
      buildPanic(null);
    } else {
      BooleanConsumer continuation = b -> {
        var side = b ? eq.a() : eq.b();
        buildReturn(serializeTermUnderTele(side, argsTerm, unit.telescope().size()));
      };

      buildIfElse(is0Term, () -> continuation.accept(true), () -> continuation.accept(false));
    }
  }

  @Override public AyaSerializer<ConDef> serialize(ConDef unit) {
    var argsTerm = "args";
    var is0Term = "is0";

    buildFramework(unit, () -> {
      buildMethod("isAvailable",
        ImmutableSeq.of(new JitParam(argsTerm, TYPE_TERMSEQ)),
        STR."\{CLASS_RESULT}<\{TYPE_IMMTERMSEQ}, \{CLASS_BOOLEAN}>", true,
        () -> buildIsAvailable(unit, argsTerm));
      appendLine();
      buildMethod("equality",
        ImmutableSeq.of(new JitParam(argsTerm, TYPE_TERMSEQ), new JitParam(is0Term, "boolean")),
        CLASS_TERM, true, () -> buildEquality(unit, argsTerm, is0Term));
    });

    return this;
  }
}
