// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.MethodRef;
import org.aya.compiler.morphism.Constants;
import org.aya.compiler.morphism.JavaUtil;
import org.aya.compiler.morphism.ir.IrClassBuilder;
import org.aya.compiler.morphism.ir.IrCodeBuilder;
import org.aya.compiler.morphism.ir.IrExpr;
import org.aya.syntax.compile.JitClass;
import org.aya.syntax.compile.JitMember;
import org.aya.syntax.core.def.ClassDef;
import org.aya.syntax.core.term.call.ClassCall;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ConstantDescs;

public final class ClassSerializer extends JitDefSerializer<ClassDef> {
  public ClassSerializer(ModuleSerializer.@NotNull MatchyRecorder recorder) {
    super(JitClass.class, recorder);
  }

  @Override protected @NotNull Class<?> callClass() { return ClassCall.class; }
  @Override protected boolean shouldBuildEmptyCall(@NotNull ClassDef unit) {
    return true;
  }

  // TODO: unify with DataSerializer#buildConstructors
  private void buildMembers(@NotNull IrCodeBuilder builder, ClassDef unit) {
    var mems = Constants.JITCLASS_MEMS;
    var memsRef = builder.bindExpr(mems.returnType(), new IrExpr.RefField(mems, IrExpr.This.INSTANCE));

    if (unit.members().isEmpty()) {
      builder.returnWith(memsRef);
      return;
    }

    builder.ifNull(new IrExpr.GetArray(memsRef, 0), cb ->
      unit.members().forEachIndexed((idx, con) ->
        cb.updateArray(memsRef, idx,
          AbstractExprSerializer.getInstance(builder, con))), null);

    builder.returnWith(memsRef);
  }

  @Override public @NotNull ClassSerializer serialize(@NotNull IrClassBuilder builder, ClassDef unit) {
    buildFramework(builder, unit, builder0 -> {
      builder0.buildMethod(
        JavaUtil.fromClass(JitMember.class).arrayType(), "membars", false,
        ImmutableSeq.empty(),
        (_, cb) -> buildMembers(cb, unit));
      if (unit.classifyingIndex() != -1) {
        builder0.buildMethod(
          ConstantDescs.CD_int, "classifyingIndex", false,
          ImmutableSeq.empty(),
          (_, cb) -> cb.returnWith(cb.bindExpr(new IrExpr.Iconst(unit.classifyingIndex()))));
      }
    });

    return this;
  }

  @Override protected @NotNull MethodRef buildConstructor(@NotNull IrClassBuilder builder, ClassDef unit) {
    return builder.buildConstructor(ImmutableSeq.empty(), (_, cb) ->
      cb.invokeSuperCon(ImmutableSeq.empty(), ImmutableSeq.empty()));
  }
}
