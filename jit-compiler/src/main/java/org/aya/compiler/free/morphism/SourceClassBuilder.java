// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.SourceBuilder;
import org.aya.compiler.free.*;
import org.aya.compiler.free.data.FieldRef;
import org.aya.compiler.free.data.MethodRef;
import org.aya.syntax.compile.CompiledAya;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public record SourceClassBuilder(
  @NotNull SourceFreeJavaBuilder parent, @NotNull ClassDesc owner,
  @NotNull SourceBuilder sourceBuilder)
  implements FreeClassBuilder, FreeJavaResolver {
  @Override
  public @NotNull FreeJavaResolver resolver() {
    return this;
  }

  @Override
  public void buildNestedClass(
    CompiledAya compiledAya,
    @NotNull String name,
    @NotNull Class<?> superclass,
    @NotNull Consumer<FreeClassBuilder> builder
  ) {
    // TODO: build annotation
    this.sourceBuilder.buildClass(owner.nested(name).displayName(), superclass, true, () ->
      builder.accept(this));
  }

  private void buildMethod(
    @NotNull String returnType,
    @NotNull String name,
    @NotNull ImmutableSeq<ClassDesc> paramTypes,
    @NotNull BiConsumer<ArgumentProvider, FreeCodeBuilder> builder
  ) {
    var params = paramTypes.map(x ->
      new AbstractSerializer.JitParam(this.sourceBuilder.nameGen().nextName(), SourceFreeJavaBuilder.toClassRef(x))
    );

    this.sourceBuilder.buildMethod(name, params, returnType, false, () -> builder.accept(
      new SourceArgumentProvider(params.map(AbstractSerializer.JitParam::name)),
      new SourceCodeBuilder(this, this.owner, this.sourceBuilder)
    ));
  }

  @Override
  public @NotNull MethodRef buildMethod(
    @NotNull ClassDesc returnType,
    @NotNull String name,
    @NotNull ImmutableSeq<ClassDesc> paramTypes,
    @NotNull BiConsumer<ArgumentProvider, FreeCodeBuilder> builder
  ) {
    buildMethod(SourceFreeJavaBuilder.toClassRef(returnType), name, paramTypes, builder);
    return new MethodRef.Default(this.owner, name, returnType, paramTypes, false);
  }

  @Override
  public void buildConstructor(
    @NotNull ImmutableSeq<ClassDesc> paramTypes,
    @NotNull BiConsumer<ArgumentProvider, FreeCodeBuilder> builder
  ) {
    buildMethod(
      "/* constructor */",
      SourceFreeJavaBuilder.toClassName(this.owner),
      paramTypes,
      builder);
  }

  @Override
  public @NotNull FieldRef buildConstantField(@NotNull ClassDesc returnType, @NotNull String name) {
    sourceBuilder.buildConstantField(SourceFreeJavaBuilder.toClassRef(returnType), name, null);
    return new FieldRef.Default(this.owner, returnType, name);
  }

  @Override
  public @NotNull MethodRef resolve(
    @NotNull ClassDesc owner,
    @NotNull String name,
    @NotNull ClassDesc returnType,
    @NotNull ImmutableSeq<ClassDesc> paramType,
    boolean isInterface
  ) {
    return new MethodRef.Default(owner, name, returnType, paramType, isInterface);
  }

  @Override
  public @NotNull FieldRef resolve(@NotNull ClassDesc owner, @NotNull String name, @NotNull ClassDesc returnType) {
    return new FieldRef.Default(owner, returnType, name);
  }
}
