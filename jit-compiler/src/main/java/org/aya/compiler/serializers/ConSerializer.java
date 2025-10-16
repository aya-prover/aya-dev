// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Result;
import org.aya.compiler.morphism.AstUtil;
import org.aya.compiler.morphism.Constants;
import org.aya.compiler.morphism.ast.AstClassBuilder;
import org.aya.compiler.morphism.ast.AstCodeBuilder;
import org.aya.compiler.morphism.ast.AstExpr;
import org.aya.compiler.morphism.ast.AstVariable;
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
      AstUtil.fromClass(JitData.class),
      ConstantDescs.CD_int, ConstantDescs.CD_boolean
    ));
  }

  @Override protected @NotNull ImmutableSeq<AstVariable> superConArgs(@NotNull AstCodeBuilder builder, ConDef unit) {
    return super.superConArgs(builder, unit).appendedAll(ImmutableSeq.of(
      AbstractExprializer.getInstance(builder, unit.dataRef),
      builder.iconst(unit.selfTele.size()),
      builder.iconst(unit.equality != null)
    ));
  }

  /// @param unit must be indexed, otherwise it should use the default impl.
  /// @see JitCon#isAvailable
  private void buildIsAvailable(
    @NotNull AstCodeBuilder builder,
    ConDef unit,
    @NotNull AstVariable normalizer,
    @NotNull AstVariable argsTerm
  ) {
    var termSeq = builder.bindExpr(new AstExpr.Invoke(Constants.SEQ_TOSEQ, argsTerm, ImmutableSeq.empty()));
    // It is too stupid to serialize pat meta solving, so we just call PatMatcher
    var patsTerm = unit.pats.map(x ->
      new PatternExprializer(builder, buildSerializerContext(normalizer), true)
        .serialize(x)
    );
    var patsSeq = AbstractExprializer.makeImmutableSeq(builder, Pat.class, patsTerm);
    var matcherTerm = builder.mkNew(PatMatcher.InferMeta.class,
      ImmutableSeq.of(normalizer));

    var matchResult = new AstExpr.Invoke(Constants.PATMATCHER_APPLY, matcherTerm,
      ImmutableSeq.of(patsSeq, termSeq));

    builder.returnWith(matchResult);
  }

  /**
   * @see ConDefLike#equality(Seq, boolean)
   */
  private void buildEquality(
    @NotNull AstCodeBuilder builder,
    ConDef unit,
    @NotNull AstVariable argsTerm,
    @NotNull AstVariable is0Term
  ) {
    var eq = unit.equality;
    assert eq != null;
    BiConsumer<AstCodeBuilder, Boolean> continuation = (cb, b) -> {
      var side = b ? eq.a() : eq.b();
      cb.returnWith(serializeTermUnderTeleWithoutNormalizer(cb, side, argsTerm, unit.telescope().size()));
    };

    builder.ifTrue(is0Term,
      then -> continuation.accept(then, true),
      otherwise -> continuation.accept(otherwise, false));
  }

  @Override public @NotNull ConSerializer serialize(@NotNull AstClassBuilder builder0, ConDef unit) {
    buildFramework(builder0, unit, builder -> {
      if (unit.pats.isNotEmpty()) {
        builder.buildMethod(
          AstUtil.fromClass(Result.class),
          "isAvailable",
          InvokeSignatureHelper.parameters(ImmutableSeq.of(Constants.CD_ImmutableSeq).view()),
          (ap, builder1) ->
            buildIsAvailable(builder1, unit, InvokeSignatureHelper.normalizer(ap), InvokeSignatureHelper.arg(ap, 0)));
      }

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
