// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import kala.control.Either;
import org.aya.compiler.MethodRef;
import org.aya.compiler.morphism.Constants;
import org.aya.compiler.morphism.ast.*;
import org.aya.generic.Modifier;
import org.aya.primitive.ShapeFactory;
import org.aya.syntax.compile.JitFn;
import org.aya.syntax.compile.JitUnit;
import org.aya.syntax.core.def.FnDef;
import org.aya.syntax.core.def.TyckAnyDef;
import org.aya.syntax.core.term.LetTerm;
import org.aya.syntax.core.term.call.FnCall;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.util.EnumSet;
import java.util.function.Consumer;

import static org.aya.compiler.morphism.Constants.CD_Term;
import static org.aya.compiler.serializers.NameSerializer.getReference;

public final class FnSerializer extends JitTeleSerializer<FnDef> {
  private final @NotNull ShapeFactory shapeFactory;

  public FnSerializer(@NotNull ShapeFactory shapeFactory, ModuleSerializer.@NotNull MatchyRecorder recorder) {
    super(JitFn.class, recorder);
    this.shapeFactory = shapeFactory;
  }

  public static int modifierFlags(@NotNull EnumSet<Modifier> modies) {
    var flag = 0;
    for (var mody : modies) flag |= 1 << mody.ordinal();
    return flag;
  }

  @Override protected @NotNull ImmutableSeq<ClassDesc> superConParams() {
    return super.superConParams().appended(ConstantDescs.CD_int);
  }

  @Override
  protected @NotNull ImmutableSeq<AstValue> superConArgs(@NotNull AstCodeBuilder builder, FnDef unit) {
    return super.superConArgs(builder, unit)
      .appended(new AstExpr.Iconst(modifierFlags(unit.modifiers())));
  }

  public static @NotNull AstVariable makeInvoke(
    @NotNull AstCodeBuilder builder,
    @NotNull ClassDesc owner,
    @NotNull AstVariable normalizer,
    @NotNull ImmutableSeq<AstVariable> args
  ) {
    var ref = new MethodRef(
      owner, "invoke", CD_Term,
      InvokeSignatureHelper.parameters(ImmutableSeq.fill(args.size(), CD_Term).view()),
      false
    );

    return AbstractExprSerializer.makeCallInvoke(builder, ref, normalizer, SeqView.narrow(args.view()));
  }

  /// Build fixed argument `invoke`
  private void buildInvokeBody(
    @NotNull AstCodeBuilder topBuilder,
    @NotNull FnDef unit,
    @NotNull AstVariable normalizer,
    @NotNull ImmutableSeq<AstVariable> argTerms
  ) {
    Consumer<AstCodeBuilder> buildFn = builder -> {
      Consumer<AstCodeBuilder> onStuckCon = cb -> {
        var stuckTerm = TermSerializer.buildFnCall(cb, FnCall.class, unit, 0, argTerms);
        cb.returnWith(stuckTerm);
      };

      var serializerContext = buildSerializerContext(normalizer);

      if (unit.is(Modifier.Opaque)) {
        onStuckCon.accept(builder);
        return;
      }

      switch (unit.body()) {
        case Either.Left(var expr) -> {
          var result = serializerContext.serializeTermUnderTele(builder, expr, argTerms);
          builder.returnWith(result);
        }
        case Either.Right(var clauses) -> {
          var ser = new PatternCompiler(argTerms, onStuckCon, serializerContext, unit.is(Modifier.Overlap));
          ser.serialize(builder, clauses.matchingsView().map(matching -> new PatternCompiler.Matching(
              matching.bindCount(), matching.patterns(), (patSer, builder0, count) -> {
              if (LetTerm.unletBody(matching.body()) instanceof FnCall call && call.tailCall()) {
                var te = new TermSerializer(builder0, serializerContext, argTerms, patSer.result.view().take(count).toSeq());
                var dummy = te.serialize(matching.body());
                assert dummy instanceof AstVariable.Local(int index) && index == -1;
              } else {
                var result = serializerContext.serializeTermUnderTele(builder0, matching.body(), patSer.result.view()
                  .take(count)
                  .toSeq());
                builder0.returnWith(result);
              }
            })
          ).toSeq());
        }
      }
    };

    if (unit.modifiers().contains(Modifier.Tailrec)) {
      topBuilder.whileTrue(buildFn);
    } else {
      buildFn.accept(topBuilder);
    }
  }

  /// @param unit must be elaborated
  public @NotNull AstDecl.Method buildInvokeForPrettyPrint(@NotNull FnDef unit) {
    var module = unit.ref().module;
    assert module != null;
    var desc = ClassDesc.of(getReference(module, null, NameSerializer.NameType.ClassName));
    var classBuilder = new AstClassBuilder(null, desc, null, MutableMap.create(), JitUnit.class);
    buildFixedInvoke(unit, classBuilder);
    return classBuilder.members().view()
      .filterIsInstance(AstDecl.Method.class)
      .find(it -> "invoke".equals(it.signature().name()))
      .get();
  }

  /**
   * Build vararg `invoke`
   */
  private void buildInvoke(
    @NotNull AstCodeBuilder builder,
    @NotNull FnDef unit,
    @NotNull MethodRef invokeMethod,
    @NotNull AstVariable normalizerTerm,
    @NotNull AstVariable argsTerm
  ) {
    var teleSize = unit.telescope().size();
    var args = AbstractExprSerializer.fromSeq(builder, CD_Term, argsTerm, teleSize);
    var result = AbstractExprSerializer.makeCallInvoke(builder, invokeMethod, normalizerTerm,
      SeqView.narrow(args.view()));
    builder.returnWith(result);
  }

  @Override protected @NotNull Class<?> callClass() { return FnCall.class; }

  @Override protected int buildShape(FnDef unit) {
    var shapeMaybe = shapeFactory.find(TyckAnyDef.make(unit));
    if (shapeMaybe.isEmpty()) return super.buildShape(unit);
    return shapeMaybe.get().shape().ordinal();
  }

  @Override public @NotNull FnSerializer serialize(@NotNull AstClassBuilder builder, FnDef unit) {
    buildFramework(builder, unit, builder0 -> {
      var fixedInvoke = buildFixedInvoke(unit, builder0);

      builder0.buildMethod(
        CD_Term, "invoke", false,
        InvokeSignatureHelper.parameters(ImmutableSeq.of(Constants.CD_Seq).view()),
        (ap, cb) ->
          buildInvoke(cb, unit, fixedInvoke, InvokeSignatureHelper.normalizer(ap), InvokeSignatureHelper.arg(ap, 0))
      );
    });

    return this;
  }

  private @NotNull MethodRef buildFixedInvoke(FnDef unit, AstClassBuilder builder) {
    return builder.buildMethod(
      CD_Term,
      "invoke", true,
      InvokeSignatureHelper.parameters(ImmutableSeq.fill(unit.telescope().size(), CD_Term).view()),
      (ap, cb) -> {
        var pre = InvokeSignatureHelper.normalizer(ap);
        var args = ImmutableSeq.fill(unit.telescope().size(),
          i -> InvokeSignatureHelper.arg(ap, i));
        buildInvokeBody(cb, unit, pre, args);
      }
    );
  }
}
