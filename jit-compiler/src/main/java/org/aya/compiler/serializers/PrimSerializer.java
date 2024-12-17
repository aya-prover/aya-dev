// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.FreeClassBuilder;
import org.aya.compiler.free.FreeCodeBuilder;
import org.aya.compiler.free.FreeJavaExpr;
import org.aya.compiler.free.FreeUtil;
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
    return super.superConParams().appended(FreeUtil.fromClass(PrimDef.ID.class));
  }

  @Override
  protected @NotNull ImmutableSeq<FreeJavaExpr> superConArgs(@NotNull FreeCodeBuilder builder, PrimDef unit) {
    return super.superConArgs(builder, unit).appended(builder.refEnum(unit.id));
  }

  @Override public @NotNull PrimSerializer serialize(@NotNull FreeClassBuilder builder, PrimDef unit) {
    buildFramework(builder, unit, _ -> { });
    return this;
  }
}
