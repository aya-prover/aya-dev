// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.FreeClassBuilder;
import org.aya.compiler.free.FreeCodeBuilder;
import org.aya.compiler.free.FreeJavaExpr;
import org.aya.compiler.free.FreeUtil;
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
      FreeUtil.fromClass(JitClass.class),
      ConstantDescs.CD_int,
      FreeUtil.fromClass(SortTerm.class)
    ));
  }

  @Override
  protected @NotNull ImmutableSeq<FreeJavaExpr> superConArgs(@NotNull FreeCodeBuilder builder, MemberDef unit) {
    return super.superConArgs(builder, unit).appendedAll(ImmutableSeq.of(
      AbstractExprializer.getInstance(builder, AnyDef.fromVar(unit.classRef())),
      builder.iconst(unit.index()),
      serializeTerm(builder, unit.type())
    ));
  }

  @Override public @NotNull MemberSerializer serialize(@NotNull FreeClassBuilder builder, MemberDef unit) {
    buildFramework(builder, unit, _ -> { });
    return this;
  }
}
