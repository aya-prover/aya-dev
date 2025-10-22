// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ast;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.FreezableMutableList;
import kala.collection.mutable.MutableLinkedHashMap;
import kala.collection.mutable.MutableMap;
import org.aya.compiler.FieldRef;
import org.aya.compiler.MethodRef;
import org.aya.compiler.morphism.JavaUtil;
import org.aya.syntax.compile.AyaMetadata;
import org.glavo.classfile.ClassHierarchyResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public record AstClassBuilder(
  @Nullable AyaMetadata metadata,
  @NotNull ClassDesc parentOrThis,
  @Nullable String nested,
  @NotNull Class<?> superclass,
  @NotNull FreezableMutableList<AstDecl> members,
  @NotNull MutableMap<ClassDesc, ClassHierarchyResolver.ClassHierarchyInfo> usedClasses,
  @NotNull MutableMap<FieldRef, Function<AstCodeBuilder, AstVariable>> fieldInitializers
) {
  public AstClassBuilder(
    @Nullable AyaMetadata metadata,
    @NotNull ClassDesc parentOrThis, @Nullable String nested,
    @NotNull MutableMap<ClassDesc, ClassHierarchyResolver.ClassHierarchyInfo> classMarkers,
    @NotNull Class<?> superclass
  ) {
    this(metadata, parentOrThis, nested, superclass,
      FreezableMutableList.create(),
      classMarkers,
      MutableLinkedHashMap.of());
  }

  public @NotNull AstDecl.Clazz build() {
    if (fieldInitializers.isNotEmpty()) {
      var codeBuilder = new AstCodeBuilder(this, FreezableMutableList.create(), new VariablePool(), false, false);
      fieldInitializers.forEach((fieldRef, init) ->
        codeBuilder.updateField(fieldRef, init.apply(codeBuilder)));
      members.append(new AstDecl.StaticInitBlock(codeBuilder.build()));
    }
    return new AstDecl.Clazz(metadata, parentOrThis, nested, superclass, members.freeze());
  }

  public @NotNull ClassDesc className() {
    return nested == null ? parentOrThis : parentOrThis.nested(nested);
  }

  public void buildNestedClass(
    @NotNull AyaMetadata ayaMetadata,
    @NotNull String name,
    @NotNull Class<?> superclass,
    @NotNull Consumer<AstClassBuilder> builder
  ) {
    var classBuilder = new AstClassBuilder(ayaMetadata, className(), name, usedClasses, superclass);
    builder.accept(classBuilder);
    members.append(classBuilder.build());
  }

  private void buildMethod(
    @NotNull MethodRef ref, boolean isStatic,
    @NotNull BiConsumer<AstArgsProvider.FnParam, AstCodeBuilder> builder
  ) {
    var codeBuilder = new AstCodeBuilder(this, FreezableMutableList.create(), new VariablePool(), ref.isConstructor(), false);
    builder.accept(new AstArgsProvider.FnParam(ref.paramTypes().size()), codeBuilder);
    members.append(new AstDecl.Method(ref, isStatic, codeBuilder.build()));
  }

  public @NotNull MethodRef buildMethod(
    @NotNull ClassDesc returnType,
    @NotNull String name, boolean isStatic,
    @NotNull ImmutableSeq<ClassDesc> paramTypes,
    @NotNull BiConsumer<AstArgsProvider.FnParam, AstCodeBuilder> builder
  ) {
    var ref = new MethodRef(className(), name, returnType, paramTypes, false);
    buildMethod(ref, isStatic, builder);
    return ref;
  }

  public @NotNull MethodRef buildMethod(
    @NotNull ClassDesc returnType,
    @NotNull String name,
    @NotNull ImmutableSeq<ClassDesc> paramTypes,
    @NotNull BiConsumer<AstArgsProvider.FnParam, AstCodeBuilder> builder
  ) {
    return buildMethod(returnType, name, false, paramTypes, builder);
  }

  public @NotNull MethodRef buildConstructor(
    @NotNull ImmutableSeq<ClassDesc> paramTypes,
    @NotNull BiConsumer<AstArgsProvider.FnParam, AstCodeBuilder> builder
  ) {
    var ref = JavaUtil.makeConstructorRef(className(), paramTypes);
    buildMethod(ref, false, builder);
    return ref;
  }

  public @NotNull FieldRef buildConstantField(
    @NotNull ClassDesc returnType,
    @NotNull String name,
    @NotNull Function<AstCodeBuilder, AstVariable> initializer
  ) {
    var ref = new FieldRef(className(), returnType, name);
    fieldInitializers.put(ref, initializer);
    members.append(new AstDecl.ConstantField(ref));
    return ref;
  }
}
