// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.LocalVariable;
import org.aya.compiler.MethodRef;
import org.aya.compiler.morphism.*;
import org.aya.compiler.morphism.ast.AstClassBuilder;
import org.aya.compiler.morphism.ast.AstCodeBuilder;
import org.aya.compiler.morphism.ast.AstExpr;
import org.aya.compiler.morphism.ast.AstVariable;
import org.aya.syntax.compile.AyaMetadata;
import org.aya.syntax.compile.JitMatchy;
import org.aya.syntax.core.def.Matchy;
import org.aya.syntax.core.repr.CodeShape;
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

  @Override protected @NotNull MethodRef buildConstructor(@NotNull AstClassBuilder builder, MatchyData unit) {
    return builder.buildConstructor(ImmutableSeq.empty(), (_, cb) ->
      cb.invokeSuperCon(ImmutableSeq.empty(), ImmutableSeq.empty())
    );
  }

  @Override protected @NotNull String className(MatchyData unit) {
    return NameSerializer.javifyClassName(unit.matchy.qualifiedName().module(), unit.matchy.qualifiedName().name());
  }

  private static @NotNull ImmutableSeq<ClassDesc> makeInvokeParameters(int capturec, int argc) {
    return InvokeSignatureHelper.parameters(ImmutableSeq.fill(capturec + argc, Constants.CD_Term).view());
  }

  public static @NotNull AstExpr makeInvoke(
    @NotNull AstCodeBuilder builder,
    @NotNull ClassDesc owner,
    @NotNull AstVariable normalizer,
    @NotNull ImmutableSeq<AstVariable> captures,
    @NotNull ImmutableSeq<AstVariable> args
  ) {
    var instance = TermExprializer.getInstance(builder, owner);
    var ref = new MethodRef(
      owner, "invoke",
      Constants.CD_Term,
      makeInvokeParameters(captures.size(), args.size()),
      false
    );

    return AbstractExprializer.makeCallInvoke(builder, ref, instance, normalizer, captures.view().appendedAll(args));
  }

  private void buildInvoke(
    @NotNull CodeBuilder builder, @NotNull MatchyData data,
    @NotNull LocalVariable pre,
    @NotNull ImmutableSeq<LocalVariable> captures, @NotNull ImmutableSeq<LocalVariable> args
  ) {
    var unit = data.matchy;
    var captureExprs = captures.map(LocalVariable::ref);
    var argExprs = args.map(LocalVariable::ref);

    Consumer<CodeBuilder> onFailed = b -> {
      var result = b.mkNew(MatchCall.class, ImmutableSeq.of(
        AbstractExprializer.getInstance(b, NameSerializer.getClassDesc(data.matchy)),
        AbstractExprializer.makeImmutableSeq(b, Term.class, captureExprs),
        AbstractExprializer.makeImmutableSeq(b, Term.class, argExprs)
      ));
      b.returnWith(result);
    };

    if (args.isEmpty()) {
      onFailed.accept(builder);
      return;
    }

    var normalizer = pre.ref();
    var serializerContext = buildSerializerContext(normalizer);

    var matching = unit.clauses().map(clause ->
      new PatternSerializer.Matching(clause.bindCount(), clause.patterns(),
        (ps, cb, binds) -> {
          var fullSeq = ps.result.view()
            .take(binds)
            .map(LocalVariable::ref)
            .appendedAll(captureExprs)
            .toSeq();
          var returns = serializerContext.serializeTermUnderTele(cb, clause.body(), fullSeq);
          cb.returnWith(returns);
        })
    );

    new PatternSerializer(argExprs, onFailed, serializerContext, false)
      .serialize(builder, matching);
  }

  /**
   * @see JitMatchy#invoke(java.util.function.UnaryOperator, Seq, Seq)
   */
  private void buildInvoke(
    @NotNull CodeBuilder builder,
    @NotNull MatchyData data,
    @NotNull MethodRef invokeRef,
    @NotNull LocalVariable normalizer,
    @NotNull LocalVariable captures, @NotNull LocalVariable args
  ) {
    var capturec = data.capturesSize;
    int argc = data.argsSize;
    var preArgs = AbstractExprializer.fromSeq(builder, Constants.CD_Term, captures.ref(), capturec)
      .view()
      .appendedAll(AbstractExprializer.fromSeq(builder, Constants.CD_Term, args.ref(), argc));
    var fullArgs = InvokeSignatureHelper.args(normalizer.ref(), preArgs);
    var invokeExpr = builder.invoke(invokeRef, builder.thisRef(), fullArgs);

    builder.returnWith(invokeExpr);
  }

  /** @see JitMatchy#type */
  private void buildType(@NotNull CodeBuilder builder, @NotNull MatchyData data, @NotNull LocalVariable captures, @NotNull LocalVariable args) {
    var captureSeq = AbstractExprializer.fromSeq(builder, Constants.CD_Term, captures.ref(), data.capturesSize);
    var argSeq = AbstractExprializer.fromSeq(builder, Constants.CD_Term, args.ref(), data.argsSize);
    var result = serializeTermUnderTeleWithoutNormalizer(builder, data.matchy.returnTypeBound(), captureSeq.appendedAll(argSeq));
    builder.returnWith(result);
  }

  @Override protected @NotNull AyaMetadata buildMetadata(@NotNull MatchyData unit) {
    var qname = unit.matchy.qualifiedName();
    return new AyaMetadataImpl(
      qname.module(), qname.name(),
      -1, -1, new CodeShape.GlobalId[0]
    );
  }

  @Override public @NotNull ClassTargetSerializer<MatchyData>
  serialize(@NotNull ClassBuilder builder0, MatchyData unit) {
    buildFramework(builder0, unit, builder -> {
      var capturec = unit.capturesSize;
      var argc = unit.argsSize;

      var fixedInvokeRef = builder.buildMethod(Constants.CD_Term, "invoke",
        makeInvokeParameters(capturec, argc), (ap, cb) -> {
        var pre = InvokeSignatureHelper.normalizer(ap);
        var captures = ImmutableSeq.fill(capturec, i -> InvokeSignatureHelper.arg(ap, i));
        var args = ImmutableSeq.fill(argc, i -> InvokeSignatureHelper.arg(ap, i + capturec));
        buildInvoke(cb, unit, pre, captures, args);
      });

      builder.buildMethod(Constants.CD_Term, "invoke", ImmutableSeq.of(
        Constants.CD_UnaryOperator, Constants.CD_Seq, Constants.CD_Seq
      ), (ap, cb) -> {
        var pre = ap.arg(0);
        var captures = ap.arg(1);
        var args = ap.arg(2);
        buildInvoke(cb, unit, fixedInvokeRef, pre, captures, args);
      });

      builder.buildMethod(Constants.CD_Term, "type", ImmutableSeq.of(
        Constants.CD_Seq, Constants.CD_Seq
      ), (ap, cb) -> {
        var captures = ap.arg(0);
        var args = ap.arg(1);
        buildType(cb, unit, captures, args);
      });
    });

    return this;
  }
}
