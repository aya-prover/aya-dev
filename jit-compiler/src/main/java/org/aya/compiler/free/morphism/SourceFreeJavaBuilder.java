// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.AbstractSerializer;
import org.aya.compiler.SourceBuilder;
import org.aya.compiler.free.*;
import org.aya.compiler.free.data.FieldRef;
import org.aya.compiler.free.data.MethodRef;
import org.aya.syntax.compile.CompiledAya;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public record SourceFreeJavaBuilder(@NotNull SourceBuilder sourceBuilder)
  implements FreeJavaBuilder<String>, FreeJavaResolver {
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

  public record SourceClassBuilder(@NotNull SourceFreeJavaBuilder parent, @NotNull ClassDesc owner,
                                   @NotNull SourceBuilder sourceBuilder)
    implements FreeClassBuilder {
    @Override public @NotNull FreeJavaResolver resolver() { return parent; }

    @Override
    public void buildNestedClass(
      CompiledAya compiledAya,
      @NotNull String name,
      @NotNull Class<?> superclass,
      @NotNull Consumer<FreeClassBuilder> builder
    ) {
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
        new AbstractSerializer.JitParam(this.sourceBuilder.nameGen().nextName(), toClassRef(x))
      );

      this.sourceBuilder.buildMethod(name, params, returnType, false, () -> builder.accept(
        new SourceArgumentProvider(params.map(AbstractSerializer.JitParam::name)),
        new SourceCodeBuilder(parent, this.owner, this.sourceBuilder)
      ));
    }

    @Override
    public @NotNull MethodRef buildMethod(
      @NotNull ClassDesc returnType,
      @NotNull String name,
      @NotNull ImmutableSeq<ClassDesc> paramTypes,
      @NotNull BiConsumer<ArgumentProvider, FreeCodeBuilder> builder
    ) {
      buildMethod(toClassRef(returnType), name, paramTypes, builder);
      return new MethodRef.Default(this.owner, name, returnType, paramTypes, false);
    }

    @Override
    public void buildConstructor(
      @NotNull ImmutableSeq<ClassDesc> superConParamTypes,
      @NotNull ImmutableSeq<FreeJavaExpr> superConArgs,
      @NotNull ImmutableSeq<ClassDesc> paramTypes,
      @NotNull BiConsumer<ArgumentProvider, FreeCodeBuilder> builder
    ) {
      buildMethod("/* constructor */", toClassName(this.owner), paramTypes, (ap, cb) -> {
        ((SourceCodeBuilder) cb).sourceBuilder()
          .appendLine("super("
                      + superConArgs.map(x -> ((SourceFreeJavaExpr) x).expr()).joinToString(", ")
            + ");");

        builder.accept(ap, cb);
      });
    }

    @Override
    public @NotNull FieldRef buildConstantField(@NotNull ClassDesc returnType, @NotNull String name) {
      sourceBuilder.buildConstantField(toClassRef(returnType), name, null);
      return new FieldRef.Default(this.owner, returnType, name);
    }
  }

  @Override
  public @NotNull String buildClass(
    @NotNull CompiledAya compiledAya,
    @NotNull ClassDesc className,
    @NotNull Class<?> superclass,
    @NotNull Consumer<FreeClassBuilder> builder
  ) {
    sourceBuilder.buildClass(className.displayName(), superclass, false, () ->
      builder.accept(new SourceClassBuilder(this, className, sourceBuilder)));
    return sourceBuilder.builder().toString();
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
