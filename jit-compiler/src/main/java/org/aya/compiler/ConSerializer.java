// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.function.BooleanConsumer;
import org.aya.generic.NameGenerator;
import org.aya.generic.State;
import org.aya.normalize.PatMatcher;
import org.aya.syntax.compile.JitCon;
import org.aya.syntax.core.def.ConDef;
import org.aya.syntax.core.def.ConDefLike;
import org.jetbrains.annotations.NotNull;

import static org.aya.compiler.NameSerializer.getClassReference;

public final class ConSerializer extends JitTeleSerializer<ConDef> {
  public static final String CLASS_STATE = getJavaReference(State.class);
  public static final String CLASS_PATMATCHER = getJavaReference(PatMatcher.class);

  public ConSerializer(@NotNull StringBuilder builder, int indent, @NotNull NameGenerator nameGen) {
    super(builder, indent, nameGen, JitCon.class);
  }

  public ConSerializer(@NotNull AbstractSerializer<?> other) {
    super(other, JitCon.class);
  }

  @Override protected void buildConstructor(ConDef unit) {
    var hasEq = unit.equality != null;
    buildConstructor(unit, ImmutableSeq.of(getInstance(getClassReference(unit.dataRef)), Boolean.toString(hasEq)));
  }

  private void buildIsAvailable(ConDef unit, @NotNull String argsTerm) {
    String matchResult;
    var termSeq = STR."\{argsTerm}.toImmutableSeq()";
    if (unit.pats.isEmpty()) {
      // not indexed data type, this constructor is always available
      matchResult = STR."\{CLASS_RESULT}.ok(\{termSeq})";
    } else {
      var patsTerm = unit.pats.map(x -> new PatternExprializer(nameGen, true).serialize(x).result());
      var patsSeq = AbstractExprializer.makeImmutableSeq(CLASS_PAT, patsTerm, CLASS_IMMSEQ);
      var matcherTerm = AbstractExprializer.makeNew(CLASS_PATMATCHER, "true", "x -> x");
      matchResult = STR."\{matcherTerm}.apply(\{patsSeq}, \{termSeq})";
    }

    buildReturn(matchResult);
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
        STR."\{CLASS_RESULT}<\{TYPE_IMMTERMSEQ}, \{CLASS_STATE}>", true,
        () -> buildIsAvailable(unit, argsTerm));
      appendLine();
      buildMethod("equality",
        ImmutableSeq.of(new JitParam(argsTerm, TYPE_TERMSEQ), new JitParam(is0Term, "boolean")),
        CLASS_TERM, true, () -> buildEquality(unit, argsTerm, is0Term));
    });

    return this;
  }
}
