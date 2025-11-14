// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.MethodRef;
import org.aya.compiler.morphism.Constants;
import org.aya.compiler.morphism.ir.*;
import org.aya.syntax.compile.AyaMetadata;
import org.aya.syntax.compile.JitMatchy;
import org.aya.syntax.core.def.Matchy;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.MatchCall;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.util.function.Consumer;

public class MatchySerializer extends ClassTargetSerializer<MatchySerializer.MatchyData> {
  public record MatchyData(
    @NotNull Matchy matchy,
    int argsSize, int capturesSize
  ) { }

  public MatchySerializer(ModuleSerializer.@NotNull MatchyRecorder recorder) {
    super(JitMatchy.class, recorder);
  }

  @Override protected @NotNull MethodRef buildConstructor(@NotNull IrClassBuilder builder, MatchyData unit) {
    return builder.buildConstructor(ImmutableSeq.empty(), cb ->
      cb.invokeSuperCon(ImmutableSeq.empty(), ImmutableSeq.empty())
    );
  }

  @Override protected @NotNull String className(MatchyData unit) {
    return NameSerializer.javifyClassName(unit.matchy.qualifiedName().module(), unit.matchy.qualifiedName().name());
  }

  private static @NotNull ImmutableSeq<ClassDesc> makeInvokeParameters(int capturec, int argc) {
    return InvokeSignatureHelper.parameters(ImmutableSeq.fill(capturec + argc, Constants.CD_Term).view());
  }

  public static @NotNull IrVariable makeInvoke(
    @NotNull IrCodeBuilder builder,
    @NotNull ClassDesc owner,
    @NotNull IrVariable normalizer,
    @NotNull ImmutableSeq<IrVariable> captures,
    @NotNull ImmutableSeq<IrValue> args
  ) {
    var ref = new MethodRef(
      owner, "invoke",
      Constants.CD_Term,
      makeInvokeParameters(captures.size(), args.size()),
      false
    );

    return AbstractExprSerializer.makeCallInvoke(builder, ref, normalizer, args.view().prependedAll(captures));
  }

  private void buildInvoke(
    @NotNull IrCodeBuilder builder, @NotNull MatchyData data,
    @NotNull IrVariable normalizer,
    @NotNull ImmutableSeq<IrVariable> captures, @NotNull ImmutableSeq<IrVariable> args
  ) {
    var unit = data.matchy;

    Consumer<IrCodeBuilder> onFailed = b -> {
      var result = b.mkNew(MatchCall.class, ImmutableSeq.of(
        AbstractExprSerializer.getInstance(b, NameSerializer.getClassDesc(data.matchy)),
        AbstractExprSerializer.makeImmutableSeq(b, Term.class, captures),
        AbstractExprSerializer.makeImmutableSeq(b, Term.class, args)
      ));
      b.returnWith(result);
    };

    if (args.isEmpty()) {
      onFailed.accept(builder);
      return;
    }

    var serializerContext = buildSerializerContext(normalizer);

    var matching = unit.clauses().map(clause ->
      new PatternCompiler.Matching(clause.bindCount(), clause.patterns(),
        (ps, cb, binds) -> {
          var fullSeq = ps.result.view()
            .take(binds)
            .appendedAll(captures)
            .toSeq();
          var returns = serializerContext.serializeTermUnderTele(cb, clause.body(), fullSeq);
          cb.returnWith(returns);
        })
    );

    new PatternCompiler(args, onFailed, serializerContext, false)
      .serialize(builder, matching);
  }

  /**
   * @see JitMatchy#invoke(java.util.function.UnaryOperator, Seq, Seq)
   */
  private void buildInvoke(
    @NotNull IrCodeBuilder builder,
    @NotNull MatchyData data,
    @NotNull MethodRef invokeRef,
    @NotNull IrVariable normalizer,
    @NotNull IrVariable captures, @NotNull IrVariable args
  ) {
    var capturec = data.capturesSize;
    int argc = data.argsSize;
    var preArgs = AbstractExprSerializer.fromSeq(builder, Constants.CD_Term, captures, capturec)
      .view()
      .appendedAll(AbstractExprSerializer.fromSeq(builder, Constants.CD_Term, args, argc));
    var fullArgs = InvokeSignatureHelper.args(normalizer, SeqView.narrow(preArgs));
    var invokeExpr = new IrExpr.Invoke(invokeRef, IrExpr.This.INSTANCE, fullArgs);

    builder.returnWith(invokeExpr);
  }

  /** @see JitMatchy#type */
  private void buildType(@NotNull IrCodeBuilder builder, @NotNull MatchyData data, @NotNull IrVariable captures, @NotNull IrVariable args) {
    var captureSeq = AbstractExprSerializer.fromSeq(builder, Constants.CD_Term, captures, data.capturesSize);
    var argSeq = AbstractExprSerializer.fromSeq(builder, Constants.CD_Term, args, data.argsSize);
    var result = serializeTermUnderTeleWithoutNormalizer(builder, data.matchy.returnTypeBound(), captureSeq.appendedAll(argSeq));
    builder.returnWith(result);
  }

  @Override protected @NotNull AyaMetadata buildMetadata(@NotNull MatchyData unit) {
    var qname = unit.matchy.qualifiedName();
    return new AyaMetadataImpl(qname.module(), qname.name());
  }

  @Override public @NotNull ClassTargetSerializer<MatchyData>
  serialize(@NotNull IrClassBuilder builder0, MatchyData unit) {
    buildFramework(builder0, unit, builder -> {
      var capturec = unit.capturesSize;
      var argc = unit.argsSize;

      var fixedInvokeRef = builder.buildMethod(Constants.CD_Term, "invoke", true,
        makeInvokeParameters(capturec, argc), cb -> {
          var pre = InvokeSignatureHelper.normalizerInLam();
          var captures = ImmutableSeq.fill(capturec, i -> InvokeSignatureHelper.arg(i));
          var args = ImmutableSeq.fill(argc, i -> InvokeSignatureHelper.arg(i + capturec));
          buildInvoke(cb, unit, pre, captures, args);
        });

      builder.buildMethod(Constants.CD_Term, "invoke", false, ImmutableSeq.of(
        Constants.CD_UnaryOperator, Constants.CD_Seq, Constants.CD_Seq
      ), cb ->
        buildInvoke(cb, unit, fixedInvokeRef, new IrVariable.Arg(0), new IrVariable.Arg(1), new IrVariable.Arg(2)));

      builder.buildMethod(Constants.CD_Term, "type", false, ImmutableSeq.of(
        Constants.CD_Seq, Constants.CD_Seq
      ), cb -> buildType(cb, unit, new IrVariable.Arg(0), new IrVariable.Arg(1)));
    });

    return this;
  }
}
