// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.SourceBuilder;
import org.aya.compiler.free.*;
import org.aya.compiler.free.data.FieldRef;
import org.aya.compiler.free.data.MethodRef;
import org.aya.compiler.serializers.ExprializeUtil;
import org.aya.syntax.compile.CompiledAya;
import org.aya.syntax.core.repr.CodeShape;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.aya.compiler.free.morphism.SourceFreeJavaBuilder.toClassName;
import static org.aya.compiler.free.morphism.SourceFreeJavaBuilder.toClassRef;

public record SourceClassBuilder(
  @NotNull SourceFreeJavaBuilder parent, @NotNull ClassDesc owner,
  @NotNull SourceBuilder sourceBuilder)
  implements FreeClassBuilder, FreeJavaResolver {
  @Override public @NotNull FreeJavaResolver resolver() { return this; }

  private void buildMetadataRecord(@NotNull String name, @NotNull String value, boolean isFirst) {
    var prepend = isFirst ? "" : ", ";
    sourceBuilder.appendLine(prepend + name + " = " + value);
  }

  @Override public void buildMetadata(@NotNull CompiledAya compiledAya) {
    sourceBuilder.appendLine("@" + toClassRef(FreeUtil.fromClass(CompiledAya.class)) + "(");
    sourceBuilder.runInside(() -> {
      buildMetadataRecord("module", SourceCodeBuilder.mkHalfArray(
        ImmutableSeq.from(compiledAya.module()).map(ExprializeUtil::makeString)
      ), true);
      buildMetadataRecord("fileModuleSize", Integer.toString(compiledAya.fileModuleSize()), false);
      buildMetadataRecord("name", ExprializeUtil.makeString(compiledAya.name()), false);
      buildMetadataRecord("assoc", Integer.toString(compiledAya.assoc()), false);
      buildMetadataRecord("shape", Integer.toString(compiledAya.shape()), false);
      buildMetadataRecord("recognition", SourceCodeBuilder.mkHalfArray(
        ImmutableSeq.from(compiledAya.recognition()).map(x ->
          SourceCodeBuilder.makeRefEnum(FreeUtil.fromClass(CodeShape.GlobalId.class), x.name())
        )
      ), false);
    });
    sourceBuilder.appendLine(")");
  }

  @Override public void buildNestedClass(
    @NotNull CompiledAya compiledAya,
    @NotNull String name,
    @NotNull Class<?> superclass,
    @NotNull Consumer<FreeClassBuilder> builder
  ) {
    buildMetadata(compiledAya);
    this.sourceBuilder.buildClass(name, toClassRef(FreeUtil.fromClass(superclass)), true, () ->
      builder.accept(new SourceClassBuilder(parent, owner.nested(name), sourceBuilder)));
  }

  private void buildMethod(
    @NotNull String returnType,
    @NotNull String name,
    @NotNull ImmutableSeq<ClassDesc> paramTypes,
    @NotNull BiConsumer<ArgumentProvider, FreeCodeBuilder> builder
  ) {
    var params = paramTypes.map(x ->
      new SourceBuilder.JitParam(sourceBuilder.nameGen.nextName(), toClassRef(x))
    );

    sourceBuilder.buildMethod(name, params, returnType, false, () -> builder.accept(
      new SourceArgumentProvider(params.map(SourceBuilder.JitParam::name)),
      new SourceCodeBuilder(this, sourceBuilder)
    ));
  }

  @Override public @NotNull MethodRef buildMethod(
    @NotNull ClassDesc returnType,
    @NotNull String name,
    @NotNull ImmutableSeq<ClassDesc> paramTypes,
    @NotNull BiConsumer<ArgumentProvider, FreeCodeBuilder> builder
  ) {
    buildMethod(toClassRef(returnType), name, paramTypes, builder);
    return new MethodRef.Default(this.owner, name, returnType, paramTypes, false);
  }

  @Override public @NotNull MethodRef buildConstructor(
    @NotNull ImmutableSeq<ClassDesc> paramTypes,
    @NotNull BiConsumer<ArgumentProvider, FreeCodeBuilder> builder
  ) {
    buildMethod(
      "/* constructor */",
      toClassName(this.owner),
      paramTypes,
      builder);

    return FreeClassBuilder.makeConstructorRef(this.owner, paramTypes);
  }

  @Override public @NotNull FieldRef buildConstantField(
    @NotNull ClassDesc returnType,
    @NotNull String name,
    @NotNull Function<FreeExprBuilder, FreeJavaExpr> initializer
  ) {
    sourceBuilder.append("public static final " + toClassRef(returnType) + " " + name + " = ");
    var codeBuilder = new SourceCodeBuilder(this, sourceBuilder); 
    var initValue = initializer.apply(codeBuilder);
    codeBuilder.appendExpr(initValue);
    sourceBuilder.append(";");
    sourceBuilder.appendLine();
    
    return new FieldRef.Default(this.owner, returnType, name);
  }

  @Override public @NotNull MethodRef resolve(
    @NotNull ClassDesc owner,
    @NotNull String name,
    @NotNull ClassDesc returnType,
    @NotNull ImmutableSeq<ClassDesc> paramType,
    boolean isInterface
  ) {
    return new MethodRef.Default(owner, name, returnType, paramType, isInterface);
  }

  @Override public @NotNull FieldRef
  resolve(@NotNull ClassDesc owner, @NotNull String name, @NotNull ClassDesc returnType) {
    return new FieldRef.Default(owner, returnType, name);
  }
}
