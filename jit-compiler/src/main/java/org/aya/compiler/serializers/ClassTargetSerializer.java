// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.*;
import org.aya.compiler.free.data.FieldRef;
import org.aya.compiler.free.data.MethodRef;
import org.aya.syntax.compile.CompiledAya;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

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

  protected @NotNull FieldRef buildInstance(@NotNull FreeClassBuilder builder) {
    return builder.buildConstantField(thisConstructor.owner(), STATIC_FIELD_INSTANCE, b ->
      b.mkNew(thisConstructor, ImmutableSeq.empty()));
  }

  protected abstract @NotNull MethodRef buildConstructor(@NotNull FreeClassBuilder builder, T unit);

  protected abstract @NotNull String className(T unit);

  protected void buildFramework(
    @Nullable CompiledAya metadata,
    @NotNull FreeClassBuilder builder,
    @NotNull T unit,
    @NotNull Consumer<FreeClassBuilder> continuation
  ) {
    var className = className(unit);
    builder.buildNestedClass(metadata, className, superClass, nestBuilder -> {
      thisConstructor = buildConstructor(nestBuilder, unit);
      buildInstance(nestBuilder);

      continuation.accept(nestBuilder);
    });
  }

  public abstract @NotNull ClassTargetSerializer<T> serialize(@NotNull FreeClassBuilder builder, T unit);

  public @NotNull FreeJavaExpr serializeTermUnderTele(
    @NotNull FreeExprBuilder builder, @NotNull Term term,
    @NotNull FreeJavaExpr argsTerm, int size
  ) {
    return serializeTermUnderTele(builder, term, AbstractExprializer.fromSeq(builder, Constants.CD_Term, argsTerm, size));
  }

  public @NotNull FreeJavaExpr serializeTermUnderTele(
    @NotNull FreeExprBuilder builder,
    @NotNull Term term,
    @NotNull ImmutableSeq<FreeJavaExpr> argTerms
  ) {
    return new TermExprializer(builder, argTerms, recorder)
      .serialize(term);
  }

  public @NotNull FreeJavaExpr serializeTerm(@NotNull FreeCodeBuilder builder, @NotNull Term term) {
    return serializeTermUnderTele(builder, term, ImmutableSeq.empty());
  }
}
