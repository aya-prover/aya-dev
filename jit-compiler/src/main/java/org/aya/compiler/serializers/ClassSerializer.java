// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.MethodRef;
import org.aya.compiler.morphism.Constants;
import org.aya.compiler.morphism.JavaUtil;
import org.aya.compiler.morphism.ast.AstClassBuilder;
import org.aya.compiler.morphism.ast.AstCodeBuilder;
import org.aya.compiler.morphism.ast.AstExpr;
import org.aya.syntax.compile.JitClass;
import org.aya.syntax.compile.JitMember;
import org.aya.syntax.core.def.ClassDef;
import org.aya.syntax.core.term.call.ClassCall;
import org.jetbrains.annotations.NotNull;

public final class ClassSerializer extends JitDefSerializer<ClassDef> {
  public ClassSerializer(ModuleSerializer.@NotNull MatchyRecorder recorder) {
    super(JitClass.class, recorder);
  }

  @Override protected @NotNull Class<?> callClass() { return ClassCall.class; }
  @Override protected boolean shouldBuildEmptyCall(@NotNull ClassDef unit) {
    return true;
  }

  // TODO: unify with DataSerializer#buildConstructors
  private void buildMembers(@NotNull AstCodeBuilder builder, ClassDef unit) {
    var mems = Constants.JITCLASS_MEMS;
    var memsRef = new AstExpr.RefField(mems, builder.thisRef());

    if (unit.members().isEmpty()) {
      builder.returnWith(memsRef);
      return;
    }

    builder.ifNull(builder.getArray(memsRef, 0), cb ->
      unit.members().forEachIndexed((idx, con) ->
        cb.updateArray(memsRef, idx,
          new AstExpr.Ref(AbstractExprializer.getInstance(builder, con)))), null);

    builder.returnWith(memsRef);
  }

  @Override public @NotNull ClassSerializer serialize(@NotNull AstClassBuilder builder, ClassDef unit) {
    buildFramework(builder, unit, builder0 -> builder0.buildMethod(
      JavaUtil.fromClass(JitMember.class).arrayType(),
      "membars",
      ImmutableSeq.empty(),
      (ap, cb) -> buildMembers(cb, unit)));

    return this;
  }

  @Override protected @NotNull MethodRef buildConstructor(@NotNull AstClassBuilder builder, ClassDef unit) {
    return builder.buildConstructor(ImmutableSeq.empty(), (ap, cb) ->
      cb.invokeSuperCon(ImmutableSeq.empty(), ImmutableSeq.empty()));
  }
}
