// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.Constants;
import org.aya.compiler.free.FreeClassBuilder;
import org.aya.compiler.free.FreeCodeBuilder;
import org.aya.compiler.free.data.LocalVariable;
import org.aya.compiler.free.data.MethodRef;
import org.aya.syntax.compile.JitMatchy;
import org.aya.syntax.core.def.Matchy;
import org.aya.syntax.core.term.call.MatchCall;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class MatchySerializer extends ClassTargetSerializer<MatchySerializer.MatchyData> {
  public record MatchyData(
    @NotNull Matchy matchy,
    int argsSize, int capturesSize
  ) { }

  public MatchySerializer(ModuleSerializer.@NotNull MatchyRecorder recorder) {
    super(JitMatchy.class, recorder);
  }

  @Override
  protected @NotNull MethodRef buildConstructor(@NotNull FreeClassBuilder builder, MatchyData unit) {
    return builder.buildConstructor(ImmutableSeq.empty(), (_, cb) -> {
      cb.invokeSuperCon(ImmutableSeq.empty(), ImmutableSeq.empty());
    });
  }

  @Override protected @NotNull String className(MatchyData unit) {
    return "";
  }

  /**
   * @see JitMatchy#invoke(Seq, Seq)
   */
  private void buildInvoke(@NotNull FreeCodeBuilder builder, @NotNull MatchyData data, @NotNull LocalVariable captures, @NotNull LocalVariable args) {
    var unit = data.matchy;
    int argc = data.argsSize;
    Consumer<FreeCodeBuilder> onFailed = b -> b.returnWith(b.aconstNull(Constants.CD_Term));

    if (argc == 0) {
      onFailed.accept(builder);
      return;
    }

    var matching = unit.clauses().map(clause ->
      new PatternSerializer.Matching(clause.bindCount(), clause.patterns(),
        (ps, cb, bindCount) -> {
          var resultSeq = AbstractExprializer.fromSeq(cb, Constants.CD_Term, ps.result.ref(), bindCount);
          var captureSeq = AbstractExprializer.fromSeq(cb, Constants.CD_Term, captures.ref(), data.capturesSize);
          var fullSeq = resultSeq.appendedAll(captureSeq);
          var returns = serializeTermUnderTele(cb, clause.body(), fullSeq);
          cb.returnWith(returns);
        })
    );

    new PatternSerializer(AbstractExprializer.fromSeq(builder, Constants.CD_Term, args.ref(), argc), onFailed, false)
      .serialize(builder, matching);
  }

  /**
   * @see JitMatchy#type(MatchCall)
   */
  private void buildType(@NotNull FreeCodeBuilder builder, @NotNull MatchyData data, @NotNull LocalVariable captures, @NotNull LocalVariable args) {
    var unit = data.matchy;
    var captureSeq = AbstractExprializer.fromSeq(builder, Constants.CD_Term, captures.ref(), data.capturesSize);
    var argSeq = AbstractExprializer.fromSeq(builder, Constants.CD_Term, args.ref(), data.argsSize);
    var result = serializeTermUnderTele(builder, data.matchy.returnTypeBound(), captureSeq.appendedAll(argSeq));
    builder.returnWith(result);
  }

  @Override
  public @NotNull ClassTargetSerializer<MatchyData> serialize(@NotNull FreeClassBuilder builder0, MatchyData unit) {
    buildFramework(null, builder0, unit, builder -> {
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
