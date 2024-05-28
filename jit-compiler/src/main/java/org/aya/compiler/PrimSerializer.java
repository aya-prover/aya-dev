// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.compile.JitPrim;
import org.aya.syntax.core.def.PrimDef;
import org.jetbrains.annotations.NotNull;

public class PrimSerializer extends JitTeleSerializer<PrimDef> {
  public PrimSerializer(@NotNull AbstractSerializer<?> parent) {
    super(parent, JitPrim.class);
  }
  @Override protected void buildConstructor(PrimDef unit) {
    super.buildConstructor(unit, ImmutableSeq.of("id"));
  }
  @Override public AyaSerializer<PrimDef> serialize(PrimDef unit) {
    buildFramework(unit, () -> { });
    return this;
  }
}
