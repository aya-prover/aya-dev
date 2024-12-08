// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.AbstractSerializer;
import org.aya.compiler.SourceBuilder;
import org.aya.compiler.free.ArgumentProvider;
import org.aya.compiler.free.FreeJava;
import org.aya.compiler.free.FreeJavaBuilder;
import org.aya.compiler.free.data.FieldData;
import org.aya.compiler.free.data.MethodData;
import org.aya.syntax.compile.CompiledAya;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public record SourceFreeJavaBuilder(@NotNull SourceBuilder builder) implements FreeJavaBuilder {
  // convert "Ljava/lang/Object;" to "java.lang.Object"
  public static @NotNull String toClassRef(@NotNull ClassDesc className) {
    // TODO: this won't work well with array
    var name = className.displayName();
    return className.packageName() + "." + name.replace('$', '.');
  }

  // convert "Ljava/lang/Object;" to "Object"
  public static @NotNull String toClassName(@NotNull ClassDesc className) {
    var name = className.displayName();
    return name.substring(name.lastIndexOf('$') + 1);
  }

  public record SourceClassBuilder(@NotNull ClassDesc owner, @NotNull SourceBuilder sourceBuilder)
    implements FreeJavaBuilder.ClassBuilder {
    @Override
    public void buildNestedClass(
      CompiledAya compiledAya,
      @NotNull String name,
      @NotNull Class<?> superclass,
      @NotNull Consumer<ClassBuilder> builder
    ) {
      this.sourceBuilder.buildClass(owner.nested(name).displayName(), superclass, true, () ->
        builder.accept(this));
    }

    private void buildMethod(
      @NotNull String returnType,
      @NotNull String name,
      @NotNull ImmutableSeq<ClassDesc> paramTypes,
      @NotNull BiConsumer<ArgumentProvider, CodeBuilder> builder
    ) {
      var params = paramTypes.map(x ->
        new AbstractSerializer.JitParam(this.sourceBuilder.nameGen().nextName(), toClassRef(x))
      );

      this.sourceBuilder.buildMethod(name, params, returnType, false, () -> builder.accept(
        new SourceArgumentProvider(params.map(AbstractSerializer.JitParam::name)),
        new SourceCodeBuilder(this.owner, this.sourceBuilder)
      ));
    }

    @Override
    public @NotNull MethodData buildMethod(
      @NotNull ClassDesc returnType,
      @NotNull String name,
      @NotNull ImmutableSeq<ClassDesc> paramTypes,
      @NotNull BiConsumer<ArgumentProvider, CodeBuilder> builder
    ) {
      buildMethod(toClassRef(returnType), name, paramTypes, builder);
      return new MethodData.Default(this.owner, name, returnType, paramTypes);
    }

    @Override
    public void buildConstructor(
      @NotNull ImmutableSeq<ClassDesc> superConParamTypes,
      @NotNull ImmutableSeq<FreeJava> superConArgs,
      @NotNull ImmutableSeq<ClassDesc> paramTypes,
      @NotNull BiConsumer<ArgumentProvider, CodeBuilder> builder
    ) {
      buildMethod("/* constructor */", toClassName(this.owner), paramTypes, (ap, cb) -> {
        ((SourceCodeBuilder) cb).sourceBuilder()
          .appendLine("super("
            + superConArgs.map(x -> ((SourceFreeJava) x).expr()).joinToString(", ")
            + ");");

        builder.accept(ap, cb);
      });
    }

    @Override
    public @NotNull FieldData buildConstantField(@NotNull ClassDesc returnType, @NotNull String name) {
      sourceBuilder.buildConstantField(toClassRef(returnType), name, null);
      return new FieldData.Default(this.owner, returnType, name);
    }
  }

  @Override
  public void buildClass(
    @NotNull CompiledAya compiledAya,
    @NotNull ClassDesc className,
    @NotNull Class<?> superclass,
    @NotNull Consumer<ClassBuilder> builder
  ) {
    this.builder.buildClass(className.displayName(), superclass, false, () ->
      builder.accept(new SourceClassBuilder(className, this.builder)));
  }

  @Override
  public @NotNull MethodData resolve(
    @NotNull ClassDesc owner,
    @NotNull String name,
    @NotNull ClassDesc returnType,
    @NotNull ImmutableSeq<ClassDesc> paramType
  ) {
    return new MethodData.Default(owner, name, returnType, paramType);
  }
}
