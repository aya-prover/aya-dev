// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism.free;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.FreezableMutableList;
import org.aya.compiler.free.*;
import org.aya.compiler.free.data.FieldRef;
import org.aya.compiler.free.data.MethodRef;
import org.aya.syntax.compile.CompiledAya;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public record FreeClassBuilderImpl(
  @Nullable CompiledAya metadata,
  @NotNull ClassDesc owner,
  @Nullable String nested,
  @NotNull Class<?> superclass,
  @NotNull FreezableMutableList<FreeDecl> members
) implements FreeClassBuilder {
  public @NotNull FreeDecl.Clazz build() {
    return new FreeDecl.Clazz(metadata, owner, nested, superclass, members.freeze());
  }

  @Override
  public void buildNestedClass(
    @NotNull CompiledAya compiledAya,
    @NotNull String name,
    @NotNull Class<?> superclass,
    @NotNull Consumer<FreeClassBuilder> builder
  ) {
    var classBuilder = new FreeClassBuilderImpl(compiledAya, owner.nested(name), superclass, FreezableMutableList.create());
    builder.accept(classBuilder);
    members.append(classBuilder.build());
  }

  private void buildMethod(
    @NotNull MethodRef ref,
    @NotNull BiConsumer<ArgumentProvider, FreeCodeBuilder> builder
  ) {
    var codeBuilder = new FreeCodeBuilderImpl(FreezableMutableList.create(), new VariablePool(ref.paramTypes().size()), ref.isConstructor(), false);
    builder.accept(new FreeArgumentProvider(ref.paramTypes().size()), codeBuilder);
    members.append(new FreeDecl.Method(ref, codeBuilder.build()));
  }

  @Override
  public @NotNull MethodRef buildMethod(
    @NotNull ClassDesc returnType,
    @NotNull String name,
    @NotNull ImmutableSeq<ClassDesc> paramTypes,
    @NotNull BiConsumer<ArgumentProvider, FreeCodeBuilder> builder
  ) {
    var ref = new MethodRef.Default(this.owner, name, returnType, paramTypes, false);
    buildMethod(ref, builder);
    return ref;
  }

  @Override
  public @NotNull MethodRef buildConstructor(
    @NotNull ImmutableSeq<ClassDesc> paramTypes,
    @NotNull BiConsumer<ArgumentProvider, FreeCodeBuilder> builder
  ) {
    var ref = FreeClassBuilder.makeConstructorRef(this.owner, paramTypes);
    buildMethod(ref, builder);
    return ref;
  }

  @Override
  public @NotNull FieldRef buildConstantField(
    @NotNull ClassDesc returnType,
    @NotNull String name,
    @NotNull Function<FreeExprBuilder, FreeJavaExpr> initializer
  ) {
    var ref = new FieldRef.Default(this.owner, returnType, name);
    var expr = (FreeExpr) initializer.apply(FreeExprBuilderImpl.INSTANCE);
    members.append(new FreeDecl.ConstantField(ref, expr));
    return ref;
  }

  @Override
  public @NotNull FreeJavaResolver resolver() {
    throw new UnsupportedOperationException("deprecated");
  }
}
