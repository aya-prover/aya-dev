// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package source;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.FieldRef;
import org.aya.compiler.MethodRef;
import org.aya.compiler.morphism.*;
import org.aya.compiler.serializers.ExprializeUtil;
import org.aya.syntax.compile.AyaMetadata;
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
) implements ClassBuilder {
  private void buildMetadataRecord(@NotNull String name, @NotNull String value, boolean isFirst) {
    var prepend = isFirst ? "" : ", ";
    sourceBuilder.appendLine(prepend + name + " = " + value);
  }

  public void buildMetadata(@NotNull AyaMetadata ayaMetadata) {
    sourceBuilder.appendLine("@" + toClassRef(AstUtil.fromClass(AyaMetadata.class)) + "(");
    sourceBuilder.runInside(() -> {
      buildMetadataRecord(AyaMetadata.NAME_MODULE, SourceCodeBuilder.mkHalfArray(
        ImmutableSeq.from(ayaMetadata.module()).map(ExprializeUtil::makeString)
      ), true);
      buildMetadataRecord(AyaMetadata.NAME_FILE_MODULE_SIZE, Integer.toString(ayaMetadata.fileModuleSize()), false);
      buildMetadataRecord(AyaMetadata.NAME_NAME, ExprializeUtil.makeString(ayaMetadata.name()), false);
      if (ayaMetadata.assoc() != -1)
        buildMetadataRecord(AyaMetadata.NAME_ASSOC, Integer.toString(ayaMetadata.assoc()), false);
      if (ayaMetadata.shape() != -1)
        buildMetadataRecord(AyaMetadata.NAME_SHAPE, Integer.toString(ayaMetadata.shape()), false);
      if (ayaMetadata.recognition().length != 0) buildMetadataRecord(AyaMetadata.NAME_RECOGNITION, SourceCodeBuilder.mkHalfArray(
        ImmutableSeq.from(ayaMetadata.recognition()).map(x ->
          SourceCodeBuilder.makeRefEnum(AstUtil.fromClass(CodeShape.GlobalId.class), x.name())
        )
      ), false);
    });
    sourceBuilder.appendLine(")");
  }

  @Override public void buildNestedClass(
    @NotNull AyaMetadata ayaMetadata,
    @NotNull String name,
    @NotNull Class<?> superclass,
    @NotNull Consumer<ClassBuilder> builder
  ) {
    buildMetadata(ayaMetadata);
    this.sourceBuilder.buildClass(name, toClassRef(AstUtil.fromClass(superclass)), true, () ->
      builder.accept(new SourceClassBuilder(parent, owner.nested(name), sourceBuilder)));
  }

  private void buildMethod(
    @NotNull String returnType,
    @NotNull String name,
    @NotNull ImmutableSeq<ClassDesc> paramTypes,
    @NotNull BiConsumer<ArgumentProvider, CodeBuilder> builder
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
    @NotNull BiConsumer<ArgumentProvider, CodeBuilder> builder
  ) {
    buildMethod(toClassRef(returnType), name, paramTypes, builder);
    return new MethodRef(this.owner, name, returnType, paramTypes, false);
  }

  @Override public @NotNull MethodRef buildConstructor(
    @NotNull ImmutableSeq<ClassDesc> paramTypes,
    @NotNull BiConsumer<ArgumentProvider, CodeBuilder> builder
  ) {
    buildMethod(
      "/* constructor */",
      toClassName(this.owner),
      paramTypes,
      builder);

    return ClassBuilder.makeConstructorRef(this.owner, paramTypes);
  }

  @Override public @NotNull FieldRef buildConstantField(
    @NotNull ClassDesc returnType,
    @NotNull String name,
    @NotNull Function<ExprBuilder, JavaExpr> initializer
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
