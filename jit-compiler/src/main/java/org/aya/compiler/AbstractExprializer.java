// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.*;
import org.aya.compiler.free.data.MethodRef;
import org.aya.syntax.core.def.AnyDef;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;

public abstract class AbstractExprializer<T> {
  protected final @NotNull FreeExprBuilder builder;

  protected AbstractExprializer(@NotNull FreeExprBuilder builder) { this.builder = builder; }

  public final @NotNull FreeJava makeImmutableSeq(@NotNull Class<?> typeName, @NotNull ImmutableSeq<FreeJava> terms) {
    return makeImmutableSeq(Constants.IMMSEQ, typeName, terms);
  }

  public final @NotNull FreeJava makeImmutableSeq(@NotNull MethodRef con, @NotNull Class<?> typeName, @NotNull ImmutableSeq<FreeJava> terms) {
    var args = builder.mkArray(FreeUtil.fromClass(typeName), terms.size(), terms);
    return builder.invoke(con, ImmutableSeq.of(args));
  }

  public final @NotNull FreeJava serializeToImmutableSeq(@NotNull Class<?> typeName, @NotNull ImmutableSeq<T> terms) {
    return makeImmutableSeq(typeName, terms.map(this::doSerialize));
  }

  /**
   * Return the reference to the {@code INSTANCE} field of the compiled class to {@param def}
   */
  public final @NotNull FreeJava getInstance(@NotNull AnyDef def) {
    return getInstance(builder, def);
  }

  public static @NotNull FreeJava getInstance(@NotNull FreeExprBuilder builder, @NotNull AnyDef def) {
    var desc = NameSerializer.getClassDesc(def);
    return builder.refField(builder.resolver().resolve(desc, AyaSerializer.STATIC_FIELD_INSTANCE, desc));
  }

  public static @NotNull FreeJava getRef(@NotNull FreeExprBuilder builder, @NotNull CallKind callType, @NotNull FreeJava call) {
    return builder.refField(builder.resolver().resolve(callType.callType, AyaSerializer.FIELD_INSTANCE, callType.refType), call);
  }

  public final @NotNull FreeJava getCallInstance(@NotNull CallKind callType, @NotNull AnyDef def) {
    return builder.refField(builder.resolver().resolve(
      NameSerializer.getClassDesc(def),
      AyaSerializer.FIELD_EMPTYCALL,
      callType.callType)
    );
  }

  public static @NotNull ImmutableSeq<FreeJava> fromSeq(
    @NotNull FreeExprBuilder builder,
    @NotNull ClassDesc elementType,
    @NotNull FreeJava theSeq,
    int size
  ) {
    return ImmutableSeq.fill(size, idx -> {
      var result = builder.invoke(Constants.SEQ_GET, theSeq, ImmutableSeq.of(builder.iconst(idx)));
      return builder.checkcast(result, elementType);
    });
  }

  /**
   * Actually perform serialization, unlike {@link #serialize}
   * which will perform some initialization after a {@code T} is obtained.
   */
  protected abstract @NotNull FreeJava doSerialize(@NotNull T term);

  /**
   * Prepare and perform {@link #doSerialize}
   */
  public abstract @NotNull FreeJava serialize(T unit);
}
