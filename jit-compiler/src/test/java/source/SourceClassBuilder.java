// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package source;

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

import static source.SourceFreeJavaBuilder.toClassName;
import static source.SourceFreeJavaBuilder.toClassRef;

public record SourceClassBuilder(
  @NotNull SourceFreeJavaBuilder parent, @NotNull ClassDesc owner,
  @NotNull SourceBuilder sourceBuilder
) implements FreeClassBuilder {
  private void buildMetadataRecord(@NotNull String name, @NotNull String value, boolean isFirst) {
    var prepend = isFirst ? "" : ", ";
    sourceBuilder.appendLine(prepend + name + " = " + value);
  }

  public void buildMetadata(@NotNull CompiledAya compiledAya) {
    sourceBuilder.appendLine("@" + toClassRef(FreeUtil.fromClass(CompiledAya.class)) + "(");
    sourceBuilder.runInside(() -> {
      buildMetadataRecord("module", SourceCodeBuilder.mkHalfArray(
        ImmutableSeq.from(compiledAya.module()).map(ExprializeUtil::makeString)
      ), true);
      buildMetadataRecord("fileModuleSize", Integer.toString(compiledAya.fileModuleSize()), false);
      buildMetadataRecord("name", ExprializeUtil.makeString(compiledAya.name()), false);
      if (compiledAya.assoc() != -1)
        buildMetadataRecord("assoc", Integer.toString(compiledAya.assoc()), false);
      if (compiledAya.shape() != -1)
        buildMetadataRecord("shape", Integer.toString(compiledAya.shape()), false);
      if (compiledAya.recognition().length != 0) buildMetadataRecord("recognition", SourceCodeBuilder.mkHalfArray(
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
    return new MethodRef(this.owner, name, returnType, paramTypes, false);
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

    return new FieldRef(this.owner, returnType, name);
  }
}
