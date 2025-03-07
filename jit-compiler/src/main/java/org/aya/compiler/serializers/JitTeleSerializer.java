// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import kala.range.primitive.IntRange;
import org.aya.compiler.LocalVariable;
import org.aya.compiler.MethodRef;
import org.aya.compiler.morphism.ClassBuilder;
import org.aya.compiler.morphism.CodeBuilder;
import org.aya.compiler.morphism.Constants;
import org.aya.compiler.morphism.JavaExpr;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.telescope.JitTele;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.util.function.Consumer;

public abstract class JitTeleSerializer<T extends TyckDef> extends JitDefSerializer<T> {
  protected JitTeleSerializer(
    @NotNull Class<? extends JitTele> superClass,
    @NotNull ModuleSerializer.MatchyRecorder recorder
  ) {
    super(superClass, recorder);
  }

  protected @NotNull ImmutableSeq<ClassDesc> superConParams() {
    return Constants.JIT_TELE_CON_PARAMS;
  }

  protected @NotNull ImmutableSeq<JavaExpr> superConArgs(@NotNull CodeBuilder builder, T unit) {
    var tele = unit.telescope();
    var size = tele.size();
    var sizeExpr = builder.iconst(size);
    var licit = tele.view().map(Param::explicit)
      .map(builder::iconst)
      .toSeq();
    var licitExpr = builder.mkArray(ConstantDescs.CD_boolean, licit.size(), licit);
    var names = tele.view().map(Param::name)
      .map(builder::aconst)
      .toSeq();
    var namesExpr = builder.mkArray(ConstantDescs.CD_String, names.size(), names);
    return ImmutableSeq.of(sizeExpr, licitExpr, namesExpr);
  }

  @Override protected @NotNull MethodRef buildConstructor(@NotNull ClassBuilder builder, T unit) {
    return builder.buildConstructor(ImmutableSeq.empty(), (_, cb) ->
      cb.invokeSuperCon(superConParams(), superConArgs(cb, unit)));
  }

  @Override protected void buildFramework(
    @NotNull ClassBuilder builder,
    @NotNull T unit,
    @NotNull Consumer<ClassBuilder> continuation
  ) {
    super.buildFramework(builder, unit, nestBuilder -> {
      if (unit.telescope().isNotEmpty()) nestBuilder.buildMethod(
        Constants.CD_Term,
        "telescope",
        ImmutableSeq.of(ConstantDescs.CD_int, Constants.CD_Seq),
        (ap, cb) -> {
          var i = ap.arg(0);
          var teleArgs = ap.arg(1);
          buildTelescope(cb, unit, i, teleArgs);
        });

      nestBuilder.buildMethod(
        Constants.CD_Term,
        "result",
        ImmutableSeq.of(Constants.CD_Seq),
        (ap, cb) -> {
          var teleArgs = ap.arg(0);
          buildResult(cb, unit, teleArgs);
        }
      );

      continuation.accept(nestBuilder);
    });
  }

  @Override protected boolean shouldBuildEmptyCall(@NotNull T unit) {
    return unit.telescope().isEmpty();
  }

  /**
   * @see JitTele#telescope(int, Term...)
   */
  protected void buildTelescope(@NotNull CodeBuilder builder, @NotNull T unit, @NotNull LocalVariable iTerm, @NotNull LocalVariable teleArgsTerm) {
    var tele = unit.telescope();

    builder.switchCase(
      iTerm,
      IntRange.closedOpen(0, tele.size()).collect(ImmutableIntSeq.factory()),
      (cb, kase) -> {
        var result = serializeTermUnderTeleWithoutNormalizer(
          cb,
          tele.get(kase).type(),
          teleArgsTerm.ref(), kase
        );

        cb.returnWith(result);
      },
      CodeBuilder::unreachable);
  }

  /**
   * @see JitTele#result
   */
  protected void buildResult(@NotNull CodeBuilder builder, @NotNull T unit, @NotNull LocalVariable teleArgsTerm) {
    var result = serializeTermUnderTeleWithoutNormalizer(
      builder,
      unit.result(),
      teleArgsTerm.ref(),
      unit.telescope().size()
    );

    builder.returnWith(result);
  }
}
