// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import kala.range.primitive.IntRange;
import org.aya.compiler.free.Constants;
import org.aya.compiler.free.FreeClassBuilder;
import org.aya.compiler.free.FreeCodeBuilder;
import org.aya.compiler.free.data.FieldRef;
import org.aya.syntax.compile.JitCon;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.core.repr.CodeShape;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.telescope.JitTele;
import org.jetbrains.annotations.NotNull;

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

  @Override
  protected void buildConstructor(
    @NotNull FreeClassBuilder builder,
    T unit,
    @NotNull FieldRef fieldInstance,
    @NotNull Consumer<FreeCodeBuilder> fieldInit
  ) {
    var tele = def.telescope();
    var size = tele.size();
    var licit = tele.view().map(Param::explicit).map(Object::toString);
    var names = tele.view().map(Param::name).map(x -> "\"" + x + "\"");

    buildSuperCall(ImmutableSeq.of(
      Integer.toString(size),
      ExprializeUtils.makeArrayFrom("boolean", licit.toImmutableSeq()),
      ExprializeUtils.makeArrayFrom("java.lang.String", names.toImmutableArray())
    ).appendedAll(ext));
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
        });
      var iTerm = "i";
      var teleArgsTerm = "teleArgs";
      buildMethod(METHOD_TELESCOPE, ImmutableSeq.of(
        new JitParam(iTerm, "int"),
        new JitParam(teleArgsTerm, TYPE_TERMSEQ)
      ), CLASS_TERM, true, () -> buildTelescope(unit, iTerm, teleArgsTerm));
      appendLine();
      buildMethod(METHOD_RESULT, ImmutableSeq.of(
        new JitParam(teleArgsTerm, TYPE_TERMSEQ)
      ), CLASS_TERM, true, () -> buildResult(unit, teleArgsTerm));
      appendLine();
      continuation.run();
    });
  }

  @Override
  protected boolean shouldBuildEmptyCall(@NotNull T unit) {
    return unit.telescope().isEmpty();
  }
  /**
   * @see JitTele#telescope(int, Term...)
   */
  protected void buildTelescope(@NotNull T unit, @NotNull String iTerm, @NotNull String teleArgsTerm) {
    var tele = unit.telescope();
    buildSwitch(iTerm, IntRange.closedOpen(0, tele.size()).collect(ImmutableIntSeq.factory()), kase ->
      buildReturn(serializeTermUnderTele(tele.get(kase).type(), teleArgsTerm, kase)), () -> buildPanic(null));
  }

  /**
   * @see JitTele#result
   */
  protected void buildResult(@NotNull T unit, @NotNull String teleArgsTerm) {
    buildReturn(serializeTermUnderTele(unit.result(), teleArgsTerm, unit.telescope().size()));
  }
}
