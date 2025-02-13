// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.util.function.BiConsumer;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Result;
import org.aya.compiler.free.*;
import org.aya.compiler.free.data.LocalVariable;
import org.aya.syntax.compile.JitCon;
import org.aya.syntax.compile.JitData;
import org.aya.syntax.core.def.ConDef;
import org.aya.syntax.core.def.ConDefLike;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.pat.PatMatcher;
import org.aya.syntax.core.term.call.ConCall;
import org.aya.syntax.core.term.call.ConCallLike;
import org.jetbrains.annotations.NotNull;

public final class ConSerializer extends JitTeleSerializer<ConDef> {
  public ConSerializer(ModuleSerializer.@NotNull MatchyRecorder recorder) {
    super(JitCon.class, recorder);
  }

  @Override protected @NotNull Class<?> callClass() { return ConCall.class; }
  @Override protected @NotNull Class<?> callBaseClass() { return ConCallLike.class; }
  @Override protected @NotNull ImmutableSeq<ClassDesc> superConParams() {
    return super.superConParams().appendedAll(ImmutableSeq.of(
      FreeUtil.fromClass(JitData.class),
      ConstantDescs.CD_int, ConstantDescs.CD_boolean
    ));
  }

  @Override protected @NotNull ImmutableSeq<FreeJavaExpr> superConArgs(@NotNull FreeCodeBuilder builder, ConDef unit) {
    return super.superConArgs(builder, unit).appendedAll(ImmutableSeq.of(
      AbstractExprializer.getInstance(builder, unit.dataRef),
      builder.iconst(unit.selfTele.size()),
      builder.iconst(unit.equality != null)
    ));
  }

  /// @param unit must be indexed, otherwise it should use the default impl.
  /// @see JitCon#isAvailable
  private void buildIsAvailable(@NotNull FreeCodeBuilder builder, ConDef unit, @NotNull LocalVariable argsTerm) {
    var termSeq = builder.invoke(Constants.SEQ_TOSEQ, argsTerm.ref(), ImmutableSeq.empty());
    // It is too stupid to serialize pat meta solving, so we just call PatMatcher
    var patsTerm = unit.pats.map(x -> new PatternExprializer(builder, true, recorder).serialize(x));
    var patsSeq = AbstractExprializer.makeImmutableSeq(builder, Pat.class, patsTerm);
    var id = builder.invoke(Constants.CLOSURE_ID, ImmutableSeq.empty());
    var matcherTerm = builder.mkNew(PatMatcher.InferMeta.class,
      ImmutableSeq.of(id));

    var matchResult = builder.invoke(Constants.PATMATCHER_APPLY, matcherTerm,
      ImmutableSeq.of(patsSeq, termSeq));

    builder.returnWith(matchResult);
  }

  /**
   * @see ConDefLike#equality(Seq, boolean)
   */
  private void buildEquality(
    @NotNull FreeCodeBuilder builder,
    ConDef unit,
    @NotNull LocalVariable argsTerm,
    @NotNull LocalVariable is0Term
  ) {
    var eq = unit.equality;
    assert eq != null;
    BiConsumer<FreeCodeBuilder, Boolean> continuation = (cb, b) -> {
      var side = b ? eq.a() : eq.b();
      cb.returnWith(serializeTermUnderTele(cb, side, argsTerm.ref(), unit.telescope().size()));
    };

    builder.ifTrue(is0Term,
      then -> continuation.accept(then, true),
      otherwise -> continuation.accept(otherwise, false));
  }

  @Override public @NotNull ConSerializer serialize(@NotNull FreeClassBuilder builder0, ConDef unit) {
    buildFramework(builder0, unit, builder -> {
      if (unit.pats.isNotEmpty()) builder.buildMethod(
        FreeUtil.fromClass(Result.class),
        "isAvailable",
        ImmutableSeq.of(Constants.CD_ImmutableSeq),
        (ap, builder1) ->
          buildIsAvailable(builder1, unit, ap.arg(0)));

      if (unit.equality != null) {
        builder.buildMethod(
          Constants.CD_Term,
          "equality",
          ImmutableSeq.of(Constants.CD_Seq, ConstantDescs.CD_boolean),
          (ap, cb) -> {
            var argsTerm = ap.arg(0);
            var is0Term = ap.arg(1);
            buildEquality(cb, unit, argsTerm, is0Term);
          });
      }
    });

    return this;
  }
}
