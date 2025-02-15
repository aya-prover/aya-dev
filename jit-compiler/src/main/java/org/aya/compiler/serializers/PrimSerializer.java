// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.morphism.AstUtil;
import org.aya.compiler.morphism.ClassBuilder;
import org.aya.compiler.morphism.CodeBuilder;
import org.aya.compiler.morphism.JavaExpr;
import org.aya.syntax.compile.JitPrim;
import org.aya.syntax.core.def.PrimDef;
import org.aya.syntax.core.term.call.PrimCall;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;

public final class PrimSerializer extends JitTeleSerializer<PrimDef> {
  public PrimSerializer(ModuleSerializer.@NotNull MatchyRecorder recorder) {
    super(JitPrim.class, recorder);
  }
  @Override protected @NotNull Class<?> callClass() { return PrimCall.class; }

  @Override protected @NotNull ImmutableSeq<ClassDesc> superConParams() {
    return super.superConParams().appended(AstUtil.fromClass(PrimDef.ID.class));
  }

  @Override
  protected @NotNull ImmutableSeq<JavaExpr> superConArgs(@NotNull CodeBuilder builder, PrimDef unit) {
    return super.superConArgs(builder, unit).appended(builder.refEnum(unit.id()));
  }

  @Override public @NotNull PrimSerializer serialize(@NotNull ClassBuilder builder, PrimDef unit) {
    buildFramework(builder, unit, _ -> { });
    return this;
  }
}
