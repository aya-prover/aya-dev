// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.morphism.JavaUtil;
import org.aya.compiler.morphism.ir.IrClassBuilder;
import org.aya.compiler.morphism.ir.IrCodeBuilder;
import org.aya.compiler.morphism.ir.IrExpr;
import org.aya.compiler.morphism.ir.IrValue;
import org.aya.syntax.compile.JitClass;
import org.aya.syntax.compile.JitMember;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.MemberDef;
import org.aya.syntax.core.term.SortTerm;
import org.aya.syntax.core.term.call.MemberCall;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;

public final class MemberSerializer extends JitTeleSerializer<MemberDef> {
  public MemberSerializer(ModuleSerializer.@NotNull MatchyRecorder recorder) { super(JitMember.class, recorder); }
  @Override protected @NotNull Class<?> callClass() { return MemberCall.class; }

  @Override
  protected @NotNull ImmutableSeq<ClassDesc> superConParams() {
    return super.superConParams().appendedAll(ImmutableSeq.of(
      JavaUtil.fromClass(JitClass.class),
      ConstantDescs.CD_int,
      JavaUtil.fromClass(SortTerm.class)
    ));
  }

  @Override
  protected @NotNull ImmutableSeq<IrValue> superConArgs(@NotNull IrCodeBuilder builder, MemberDef unit) {
    return super.superConArgs(builder, unit).appendedAll(ImmutableSeq.of(
      AbstractExprSerializer.getInstance(builder, AnyDef.fromVar(unit.classRef())),
      new IrExpr.Iconst(unit.index()),
      serializeTermWithoutNormalizer(builder, unit.type())
    ));
  }

  @Override public @NotNull MemberSerializer serialize(@NotNull IrClassBuilder builder, MemberDef unit) {
    buildFramework(builder, unit, _ -> { });
    return this;
  }
}
