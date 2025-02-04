// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.Constants;
import org.aya.compiler.free.FreeClassBuilder;
import org.aya.compiler.free.FreeCodeBuilder;
import org.aya.compiler.free.data.MethodRef;
import org.aya.compiler.free.data.LocalVariable;
import org.aya.syntax.compile.CompiledAya;
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

  @Override protected @NotNull MethodRef buildConstructor(@NotNull FreeClassBuilder builder, MatchyData unit) {
    return builder.buildConstructor(ImmutableSeq.empty(), (_, cb) ->
      cb.invokeSuperCon(ImmutableSeq.empty(), ImmutableSeq.empty())
    );
  }

  @Override protected @NotNull String className(MatchyData unit) {
    return NameSerializer.javifyClassName(unit.matchy.qualifiedName().module(), unit.matchy.qualifiedName().name());
  }

  public static @NotNull MethodRef resolveInvoke(@NotNull ClassDesc owner, int capturec, int argc) {
    return new MethodRef(
      owner, "invoke",
      Constants.CD_Term, ImmutableSeq.fill(capturec + argc, Constants.CD_Term),
      false
    );
  }

  private void buildInvoke(
    @NotNull FreeCodeBuilder builder, @NotNull MatchyData data,
    @NotNull ImmutableSeq<LocalVariable> captures, @NotNull ImmutableSeq<LocalVariable> args
  ) {
    var unit = data.matchy;
    var captureExprs = captures.map(LocalVariable::ref);
    var argExprs = args.map(LocalVariable::ref);

    Consumer<FreeCodeBuilder> onFailed = b -> {
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

    var matching = unit.clauses().map(clause ->
      new PatternSerializer.Matching(clause.bindCount(), clause.patterns(),
        (ps, cb, binds) -> {
          var resultSeq = ps.result.take(binds).map(LocalVariable::ref);
          var fullSeq = resultSeq.appendedAll(captureExprs);
          // assert fullSeq.size() == binds;
          var returns = serializeTermUnderTele(cb, clause.body(), fullSeq);
          cb.returnWith(returns);
        })
    );

    new PatternSerializer(argExprs, onFailed, false)
      .serialize(builder, matching);
  }

  /**
   * @see JitMatchy#invoke(Seq, Seq)
   */
  private void buildInvoke(
    @NotNull FreeCodeBuilder builder, @NotNull MatchyData data,
    @NotNull LocalVariable captures, @NotNull LocalVariable args
  ) {
    var capturec = data.capturesSize;
    int argc = data.argsSize;
    var invokeRef = resolveInvoke(NameSerializer.getClassDesc(data.matchy), capturec, argc);
    var invokeExpr = builder.invoke(invokeRef, builder.thisRef(),
      AbstractExprializer.fromSeq(builder, Constants.CD_Term, captures.ref(), capturec)
        .appendedAll(AbstractExprializer.fromSeq(builder, Constants.CD_Term, args.ref(), argc))
    );

    builder.returnWith(invokeExpr);
  }

  /** @see JitMatchy#type */
  private void buildType(@NotNull FreeCodeBuilder builder, @NotNull MatchyData data, @NotNull LocalVariable captures, @NotNull LocalVariable args) {
    var captureSeq = AbstractExprializer.fromSeq(builder, Constants.CD_Term, captures.ref(), data.capturesSize);
    var argSeq = AbstractExprializer.fromSeq(builder, Constants.CD_Term, args.ref(), data.argsSize);
    var result = serializeTermUnderTele(builder, data.matchy.returnTypeBound(), captureSeq.appendedAll(argSeq));
    builder.returnWith(result);
  }

  @Override protected @NotNull CompiledAya buildMetadata(@NotNull MatchyData unit) {
    var qname = unit.matchy.qualifiedName();
    return new CompiledAyaImpl(
      qname.module(), qname.name(),
      -1, -1, new CodeShape.GlobalId[0]
    );
  }

  @Override public @NotNull ClassTargetSerializer<MatchyData>
  serialize(@NotNull FreeClassBuilder builder0, MatchyData unit) {
    buildFramework(builder0, unit, builder -> {
      var capturec = unit.capturesSize;
      var argc = unit.argsSize;

      builder.buildMethod(Constants.CD_Term, "invoke", ImmutableSeq.fill(capturec + argc, Constants.CD_Term),
        (ap, cb) -> {
          var captures = ImmutableSeq.fill(capturec, ap::arg);
          var args = ImmutableSeq.fill(argc, i -> ap.arg(i + capturec));
          buildInvoke(cb, unit, captures, args);
        });

      builder.buildMethod(Constants.CD_Term, "invoke", ImmutableSeq.of(
        Constants.CD_Seq, Constants.CD_Seq
      ), (ap, cb) -> {
        var captures = ap.arg(0);
        var args = ap.arg(1);
        buildInvoke(cb, unit, captures, args);
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
