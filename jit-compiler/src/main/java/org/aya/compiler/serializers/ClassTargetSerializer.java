// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.FieldRef;
import org.aya.compiler.MethodRef;
import org.aya.compiler.morphism.*;
import org.aya.syntax.compile.AyaMetadata;
import org.aya.syntax.core.repr.CodeShape;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.QPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnknownNullability;

import java.lang.annotation.Annotation;
import java.util.function.Consumer;

import static org.aya.compiler.serializers.AyaSerializer.STATIC_FIELD_INSTANCE;

public abstract class ClassTargetSerializer<T> {
  protected final @NotNull Class<?> superClass;
  protected final @NotNull ModuleSerializer.MatchyRecorder recorder;
  protected @UnknownNullability MethodRef thisConstructor;

  public ClassTargetSerializer(@NotNull Class<?> superClass, @NotNull ModuleSerializer.MatchyRecorder recorder) {
    this.superClass = superClass;
    this.recorder = recorder;
  }

  public record AyaMetadataImpl(
    @Override @NotNull String[] module, @Override int fileModuleSize,
    @Override @NotNull String name, @Override int assoc, @Override int shape,
    @Override @NotNull CodeShape.GlobalId[] recognition
  ) implements AyaMetadata {
    public AyaMetadataImpl(
      @NotNull QPath path, @NotNull String name, int assoc, int shape,
      @NotNull CodeShape.GlobalId[] recognition
    ) {
      this(path.module().module().toArray(new String[0]), path.fileModuleSize(), name, assoc, shape, recognition);
    }
    @Override public Class<? extends Annotation> annotationType() { return AyaMetadata.class; }
  }

  protected @NotNull FieldRef buildInstance(@NotNull ClassBuilder builder) {
    return builder.buildConstantField(thisConstructor.owner(), STATIC_FIELD_INSTANCE, b ->
      b.mkNew(thisConstructor, ImmutableSeq.empty()));
  }

  protected abstract @NotNull MethodRef buildConstructor(@NotNull ClassBuilder builder, T unit);

  protected abstract @NotNull String className(T unit);

  protected abstract @NotNull AyaMetadata buildMetadata(T unit);

  protected void buildFramework(
    @NotNull ClassBuilder builder,
    @NotNull T unit,
    @NotNull Consumer<ClassBuilder> continuation
  ) {
    var className = className(unit);
    builder.buildNestedClass(buildMetadata(unit), className, superClass, nestBuilder -> {
      thisConstructor = buildConstructor(nestBuilder, unit);
      buildInstance(nestBuilder);

      continuation.accept(nestBuilder);
    });
  }

  public abstract @NotNull ClassTargetSerializer<T> serialize(@NotNull ClassBuilder builder, T unit);

  public @NotNull SerializerContext buildSerializerContext(@NotNull FreeJavaExpr normalizer) {
    return new SerializerContext(normalizer, recorder);
  }

  /// Construct a {@link SerializerContext} with a no-op normalizer
  public @NotNull SerializerContext buildSerializerContext(@NotNull FreeExprBuilder builder) {
    return new SerializerContext(builder.invoke(Constants.CLOSURE_ID, ImmutableSeq.empty()), recorder);
  }

  public @NotNull FreeJavaExpr serializeTermUnderTeleWithoutNormalizer(
    @NotNull FreeExprBuilder builder, @NotNull Term term,
    @NotNull FreeJavaExpr argsTerm, int size
  ) {
    return serializeTermUnderTeleWithoutNormalizer(builder, term, AbstractExprializer.fromSeq(builder, Constants.CD_Term, argsTerm, size));
  }

  public @NotNull FreeJavaExpr serializeTermUnderTeleWithoutNormalizer(
    @NotNull FreeExprBuilder builder,
    @NotNull Term term,
    @NotNull ImmutableSeq<JavaExpr> argTerms
  ) {
    return new TermExprializer(builder, buildSerializerContext(builder), argTerms)
      .serialize(term);
  }

  public @NotNull FreeJavaExpr serializeTermWithoutNormalizer(@NotNull FreeCodeBuilder builder, @NotNull Term term) {
    return serializeTermUnderTeleWithoutNormalizer(builder, term, ImmutableSeq.empty());
  }
}
