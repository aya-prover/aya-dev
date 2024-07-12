// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.compile.JitClass;
import org.aya.syntax.compile.JitMember;
import org.aya.syntax.core.def.ClassDef;
import org.aya.syntax.core.term.call.ClassCall;
import org.jetbrains.annotations.NotNull;

import static org.aya.compiler.AyaSerializer.CLASS_IMMSEQ;
import static org.aya.compiler.ExprializeUtils.getJavaReference;
import static org.aya.compiler.NameSerializer.getClassReference;

public final class ClassSerializer extends JitTeleSerializer<ClassDef> {
  public static final String CLASS_JITMEMBERS = getJavaReference(JitMember.class);
  public static final String CLASS_CLASSCALL = getJavaReference(ClassCall.class);
  public static final String FIELD_MEMBERS = "members";
  public static final String METHOD_MEMBARS = "membars";

  public ClassSerializer(@NotNull SourceBuilder builder) {
    super(builder, JitClass.class);
  }

  @Override
  protected @NotNull String callClass() {
    return CLASS_CLASSCALL;
  }

  @Override
  protected void buildConstructor(ClassDef unit) {
    buildSuperCall(ImmutableSeq.empty());
  }

  private void buildMembers(ClassDef unit) {
    buildIf(STR."\{FIELD_MEMBERS} == null", () ->
      buildUpdate(FIELD_MEMBERS, ExprializeUtils.makeArrayFrom(CLASS_JITMEMBERS, unit.members().map(mem ->
        ExprializeUtils.getInstance(getClassReference(mem.ref())))
      )));

    buildReturn(FIELD_MEMBERS);
  }

  @Override
  public AbstractSerializer<ClassDef> serialize(ClassDef unit) {
    buildFramework(unit, () ->
      buildMethod(METHOD_MEMBARS, ImmutableSeq.empty(), STR."\{CLASS_IMMSEQ}<\{CLASS_JITMEMBERS}>", true,
        () -> buildMembers(unit)));

    return this;
  }
}
