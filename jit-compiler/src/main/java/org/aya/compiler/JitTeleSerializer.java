// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import kala.range.primitive.IntRange;
import org.aya.compiler.free.Constants;
import org.aya.compiler.free.FreeClassBuilder;
import org.aya.compiler.free.FreeCodeBuilder;
import org.aya.compiler.free.FreeJavaExpr;
import org.aya.compiler.free.data.FieldRef;
import org.aya.syntax.compile.JitCon;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.core.repr.CodeShape;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.telescope.JitTele;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.util.function.Consumer;

import static org.aya.compiler.AyaSerializer.CLASS_SEQ;
import static org.aya.compiler.AyaSerializer.CLASS_TERM;

public abstract class JitTeleSerializer<T extends TyckDef> extends JitDefSerializer<T> {
  public static final String CLASS_JITCON = ExprializeUtils.getJavaRef(JitCon.class);
  public static final String CLASS_GLOBALID = ExprializeUtils.makeSub(ExprializeUtils.getJavaRef(CodeShape.class), ExprializeUtils.getJavaRef(CodeShape.GlobalId.class));
  public static final String METHOD_TELESCOPE = "telescope";
  public static final String METHOD_RESULT = "result";
  public static final String TYPE_TERMSEQ = CLASS_SEQ + "<" + CLASS_TERM + ">";

  protected JitTeleSerializer(
    @NotNull Class<? extends JitTele> superClass
  ) {
    super(superClass);
  }

  protected @NotNull ImmutableSeq<ClassDesc> superConParams() {
    return Constants.JIT_TELE_CON_PARAMS;
  }

  protected @NotNull ImmutableSeq<FreeJavaExpr> superConArgs(@NotNull FreeCodeBuilder builder, T unit) {
    var tele = unit.telescope();
    var size = tele.size();
    var sizeExpr = builder.iconst(size);
    var licit = tele.view().map(Param::explicit)
      .map(x -> builder.iconst(x))
      .toImmutableSeq();
    var licitExpr = builder.mkArray(ConstantDescs.CD_Boolean, licit.size(), licit);
    var names = tele.view().map(Param::name)
      .map(x -> builder.aconst(x))
      .toImmutableSeq();
    var namesExpr = builder.mkArray(ConstantDescs.CD_String, names.size(), names);
    return ImmutableSeq.of(sizeExpr, licitExpr, namesExpr);
  }

  @Override
  protected void buildConstructor(
    @NotNull FreeClassBuilder builder,
    T unit,
    @NotNull FieldRef fieldInstance,
    @NotNull Consumer<FreeCodeBuilder> cont
  ) {
    builder.buildConstructor(ImmutableSeq.empty(), (ap, cb) -> {
      cb.invokeSuperCon(superConParams(), superConArgs(cb, unit));
      cb.updateField(fieldInstance, cb.thisRef());
      cont.accept(cb);
    });
  }

  @Override
  protected void buildFramework(
    @NotNull FreeClassBuilder builder,
    @NotNull T unit,
    @NotNull Consumer<FreeClassBuilder> continuation
  ) {
    super.buildFramework(builder, unit, nestBuilder -> {
      nestBuilder.buildMethod(
        Constants.CD_Term,
        METHOD_TELESCOPE,
        ImmutableSeq.of(ConstantDescs.CD_int, Constants.CD_Seq),
        (ap, cb) -> {
          var i = ap.arg(0);
          var teleArgs = ap.arg(1);
          buildTelescope(cb, unit, i, teleArgs);
        });

      nestBuilder.buildMethod(
        Constants.CD_Term,
        METHOD_RESULT,
        ImmutableSeq.of(Constants.CD_Seq),
        (ap, cb) -> {
          var teleArgs = ap.arg(0);
          buildResult(cb, unit, teleArgs);
        }
      );

      continuation.accept(nestBuilder);
    });
  }

  @Override
  protected boolean shouldBuildEmptyCall(@NotNull T unit) {
    return unit.telescope().isEmpty();
  }

  /**
   * @see JitTele#telescope(int, Term...)
   */
  protected void buildTelescope(@NotNull FreeCodeBuilder builder, @NotNull T unit, @NotNull FreeJavaExpr iTerm, @NotNull FreeJavaExpr teleArgsTerm) {
    var tele = unit.telescope();

    builder.switchCase(
      iTerm,
      IntRange.closedOpen(0, tele.size()).collect(ImmutableIntSeq.factory()),
      (cb, kase) -> {
        var result = serializeTermUnderTele(
          cb,
          tele.get(kase).type(),
          teleArgsTerm, kase
        );

        cb.returnWith(result);
      },
      AyaSerializer::buildPanic);
  }

  /**
   * @see JitTele#result
   */
  protected void buildResult(@NotNull FreeCodeBuilder builder, @NotNull T unit, @NotNull FreeJavaExpr teleArgsTerm) {
    var result = serializeTermUnderTele(
      builder,
      unit.result(),
      teleArgsTerm,
      unit.telescope().size()
    );

    builder.returnWith(result);
  }
}
