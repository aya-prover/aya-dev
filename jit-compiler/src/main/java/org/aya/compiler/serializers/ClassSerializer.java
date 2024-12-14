// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.Constants;
import org.aya.compiler.free.FreeClassBuilder;
import org.aya.compiler.free.FreeCodeBuilder;
import org.aya.compiler.free.FreeUtil;
import org.aya.compiler.free.data.MethodRef;
import org.aya.syntax.compile.JitClass;
import org.aya.syntax.compile.JitMember;
import org.aya.syntax.core.def.ClassDef;
import org.aya.syntax.core.term.call.ClassCall;
import org.jetbrains.annotations.NotNull;

public final class ClassSerializer extends JitDefSerializer<ClassDef> {
  public ClassSerializer() { super(JitClass.class); }

  @Override protected @NotNull Class<?> callClass() { return ClassCall.class; }
  @Override protected boolean shouldBuildEmptyCall(@NotNull ClassDef unit) {
    return true;
  }

  // TODO: unify with DataSerializer#buildConstructors
  private void buildMembers(@NotNull FreeCodeBuilder builder, ClassDef unit) {
    var mems = Constants.JITCLASS_MEMS;
    var memsRef = builder.refField(mems, builder.thisRef());

    if (unit.members().isEmpty()) {
      builder.returnWith(memsRef);
      return;
    }

    builder.ifNull(builder.getArray(memsRef, 0), cb -> {
      unit.members().forEachIndexed((idx, con) -> {
        cb.updateArray(memsRef, idx, AbstractExprializer.getInstance(builder, con));
      });
    }, null);

    builder.returnWith(memsRef);
  }

  @Override public @NotNull ClassSerializer serialize(@NotNull FreeClassBuilder builder, ClassDef unit) {
    buildFramework(builder, unit, builder0 -> {
      builder0.buildMethod(
        FreeUtil.fromClass(JitMember.class).arrayType(),
        "membars",
        ImmutableSeq.empty(),
        (ap, cb) -> buildMembers(cb, unit));
    });

    return this;
  }

  @Override protected @NotNull MethodRef buildConstructor(@NotNull FreeClassBuilder builder, ClassDef unit) {
    return builder.buildConstructor(ImmutableSeq.empty(), (ap, cb) ->
      cb.invokeSuperCon(ImmutableSeq.empty(), ImmutableSeq.empty()));
  }
}
