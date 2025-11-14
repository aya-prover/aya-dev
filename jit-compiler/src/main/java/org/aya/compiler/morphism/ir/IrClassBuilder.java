// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ir;

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

public record IrClassBuilder(
  @Nullable AyaMetadata metadata,
  @NotNull ClassDesc parentOrThis,
  @Nullable String nested,
  @NotNull Class<?> superclass,
  @NotNull FreezableMutableList<IrDecl> members,
  @NotNull MutableMap<ClassDesc, ClassHierarchyResolver.ClassHierarchyInfo> usedClasses,
  @NotNull MutableMap<FieldRef, Function<IrCodeBuilder, IrVariable>> fieldInitializers
) {
  public IrClassBuilder(
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

  public @NotNull IrDecl.Clazz build() {
    if (fieldInitializers.isNotEmpty()) {
      var codeBuilder = new IrCodeBuilder(this, FreezableMutableList.create(), new VariablePool(), false, false);
      fieldInitializers.forEach((fieldRef, init) ->
        codeBuilder.updateField(fieldRef, init.apply(codeBuilder)));
      members.append(new IrDecl.StaticInitBlock(codeBuilder.build()));
    }
    return new IrDecl.Clazz(metadata, parentOrThis, nested, superclass, members.freeze());
  }

  public @NotNull ClassDesc className() {
    return nested == null ? parentOrThis : parentOrThis.nested(nested);
  }

  public void buildNestedClass(
    @NotNull AyaMetadata ayaMetadata,
    @NotNull String name,
    @NotNull Class<?> superclass,
    @NotNull Consumer<IrClassBuilder> builder
  ) {
    var classBuilder = new IrClassBuilder(ayaMetadata, className(), name, usedClasses, superclass);
    builder.accept(classBuilder);
    members.append(classBuilder.build());
  }

  private void buildMethod(
    @NotNull MethodRef ref, boolean isStatic,
    @NotNull BiConsumer<IrArgsProvider.FnParam, IrCodeBuilder> builder
  ) {
    var codeBuilder = new IrCodeBuilder(this, FreezableMutableList.create(), new VariablePool(), ref.isConstructor(), false);
    builder.accept(new IrArgsProvider.FnParam(ref.paramTypes().size()), codeBuilder);
    members.append(new IrDecl.Method(ref, isStatic, codeBuilder.build()));
  }

  public @NotNull MethodRef buildMethod(
    @NotNull ClassDesc returnType,
    @NotNull String name, boolean isStatic,
    @NotNull ImmutableSeq<ClassDesc> paramTypes,
    @NotNull BiConsumer<IrArgsProvider.FnParam, IrCodeBuilder> builder
  ) {
    var ref = new MethodRef(className(), name, returnType, paramTypes, false);
    buildMethod(ref, isStatic, builder);
    return ref;
  }

  public @NotNull MethodRef buildConstructor(
    @NotNull ImmutableSeq<ClassDesc> paramTypes,
    @NotNull BiConsumer<IrArgsProvider.FnParam, IrCodeBuilder> builder
  ) {
    var ref = JavaUtil.makeConstructorRef(className(), paramTypes);
    buildMethod(ref, false, builder);
    return ref;
  }

  public @NotNull FieldRef buildConstantField(
    @NotNull ClassDesc returnType,
    @NotNull String name,
    @NotNull Function<IrCodeBuilder, IrVariable> initializer
  ) {
    var ref = new FieldRef(className(), returnType, name);
    fieldInitializers.put(ref, initializer);
    members.append(new IrDecl.ConstantField(ref));
    return ref;
  }
}
