// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.compile.JitMember;
import org.aya.syntax.core.def.MemberDef;
import org.jetbrains.annotations.NotNull;

import static org.aya.compiler.NameSerializer.getClassReference;

public final class MemberSerializer extends JitTeleSerializer<MemberDef> {
  public MemberSerializer(@NotNull SourceBuilder builder) { super(builder, JitMember.class); }
  @Override protected @NotNull String callClass() { return TermExprializer.CLASS_MEMCALL; }

  @Override protected void buildConstructor(MemberDef unit) {
    buildConstructor(unit, ImmutableSeq.of(
      ExprializeUtils.getInstance(getClassReference(unit.classRef())),
      Integer.toString(unit.index())
    ));
  }

  @Override public AbstractSerializer<MemberDef> serialize(MemberDef unit) {
    buildFramework(unit, () -> { });
    return this;
  }
}
