// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Result;
import org.aya.compiler.morphism.Constants;
import org.aya.compiler.morphism.JavaUtil;
import org.aya.compiler.morphism.ir.*;
import org.aya.syntax.compile.JitCon;
import org.aya.syntax.compile.JitData;
import org.aya.syntax.core.def.ConDef;
import org.aya.syntax.core.def.ConDefLike;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.pat.PatMatcher;
import org.aya.syntax.core.term.call.ConCall;
import org.aya.syntax.core.term.call.ConCallLike;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.util.function.BiConsumer;

public final class ConSerializer extends JitTeleSerializer<ConDef> {
  public ConSerializer(ModuleSerializer.@NotNull MatchyRecorder recorder) {
    super(JitCon.class, recorder);
  }

  @Override protected @NotNull Class<?> callClass() { return ConCall.class; }
  @Override protected @NotNull Class<?> callBaseClass() { return ConCallLike.class; }
  @Override protected @NotNull ImmutableSeq<ClassDesc> superConParams() {
    return super.superConParams().appendedAll(ImmutableSeq.of(
      JavaUtil.fromClass(JitData.class),
      ConstantDescs.CD_int, ConstantDescs.CD_boolean
    ));
  }

  @Override protected @NotNull ImmutableSeq<IrValue> superConArgs(@NotNull IrCodeBuilder builder, ConDef unit) {
    return super.superConArgs(builder, unit).appendedAll(ImmutableSeq.of(
      AbstractExprSerializer.getInstance(builder, unit.dataRef),
      new IrExpr.Iconst(unit.selfTele.size()),
      new IrExpr.Bconst(unit.equality != null)
    ));
  }

  /// @param unit must be indexed, otherwise it should use the default impl.
  /// @see JitCon#isAvailable
  private void buildIsAvailable(
    @NotNull IrCodeBuilder builder,
    ConDef unit,
    @NotNull IrVariable normalizer,
    @NotNull IrVariable argsTerm
  ) {
    var invoke = new IrExpr.Invoke(Constants.SEQ_TOSEQ, argsTerm, ImmutableSeq.empty());
    var termSeq = builder.bindExpr(invoke.methodRef().returnType(), invoke);
    // It is too stupid to serialize pat meta solving, so we just call PatMatcher
    var patsTerm = unit.pats.map(x ->
      new PatternSerializer(builder, buildSerializerContext(normalizer), true)
        .serialize(x)
    );
    var patsSeq = AbstractExprSerializer.makeImmutableSeq(builder, Pat.class, patsTerm);
    var matcherTerm = builder.mkNew(PatMatcher.InferMeta.class,
      ImmutableSeq.of(normalizer));

    var matchResult = new IrExpr.Invoke(Constants.PATMATCHER_APPLY, matcherTerm,
      ImmutableSeq.of(patsSeq, termSeq));

    builder.returnWith(matchResult);
  }

  /**
   * @see ConDefLike#equality(Seq, boolean)
   */
  private void buildEquality(
    @NotNull IrCodeBuilder builder,
    ConDef unit,
    @NotNull IrVariable argsTerm,
    @NotNull IrVariable is0Term
  ) {
    var eq = unit.equality;
    assert eq != null;
    BiConsumer<IrCodeBuilder, Boolean> continuation = (cb, b) -> {
      var side = b ? eq.a() : eq.b();
      cb.returnWith(serializeTermUnderTeleWithoutNormalizer(cb, side, argsTerm, unit.telescope().size()));
    };

    builder.ifTrue(is0Term,
      then -> continuation.accept(then, true),
      otherwise -> continuation.accept(otherwise, false));
  }

  @Override public @NotNull ConSerializer serialize(@NotNull IrClassBuilder builder0, ConDef unit) {
    buildFramework(builder0, unit, builder -> {
      if (unit.pats.isNotEmpty()) {
        builder.buildMethod(
          JavaUtil.fromClass(Result.class), "isAvailable", false,
          InvokeSignatureHelper.parameters(ImmutableSeq.of(Constants.CD_ImmutableSeq).view()),
          (ap, builder1) ->
            buildIsAvailable(builder1, unit, InvokeSignatureHelper.normalizer(ap), InvokeSignatureHelper.arg(ap, 0)));
      }

      if (unit.equality != null) {
        builder.buildMethod(Constants.CD_Term, "equality", false,
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
