// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.Constants;
import org.aya.compiler.free.FreeExprBuilder;
import org.aya.compiler.free.FreeJavaExpr;
import org.aya.compiler.free.FreeUtil;
import org.aya.compiler.free.data.MethodRef;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.TyckDef;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;

public abstract class AbstractExprializer<T> {
  protected final @NotNull FreeExprBuilder builder;

  protected AbstractExprializer(@NotNull FreeExprBuilder builder) { this.builder = builder; }

  public @NotNull FreeJavaExpr makeImmutableSeq(
    @NotNull Class<?> typeName,
    @NotNull ImmutableSeq<FreeJavaExpr> terms
  ) {
    return makeImmutableSeq(builder, typeName, terms);
  }

  public @NotNull FreeJavaExpr serializeToImmutableSeq(
    @NotNull Class<?> typeName,
    @NotNull ImmutableSeq<T> terms
  ) {
    var sered = terms.map(this::doSerialize);
    return makeImmutableSeq(typeName, sered);
  }

  public static @NotNull FreeJavaExpr makeImmutableSeq(
    @NotNull FreeExprBuilder builder,
    @NotNull Class<?> typeName,
    @NotNull ImmutableSeq<FreeJavaExpr> terms
  ) {
    return makeImmutableSeq(builder, Constants.IMMSEQ, typeName, terms);
  }

  public static @NotNull FreeJavaExpr makeImmutableSeq(
    @NotNull FreeExprBuilder builder,
    @NotNull MethodRef con,
    @NotNull Class<?> typeName,
    @NotNull ImmutableSeq<FreeJavaExpr> terms
  ) {
    var args = builder.mkArray(FreeUtil.fromClass(typeName), terms.size(), terms);
    return builder.invoke(con, ImmutableSeq.of(args));
  }

  /**
   * Return the reference to the {@code INSTANCE} field of the compiled class to {@param def}
   */
  public final @NotNull FreeJavaExpr getInstance(@NotNull AnyDef def) {
    return getInstance(builder, def);
  }

  public static @NotNull FreeJavaExpr getInstance(@NotNull FreeExprBuilder builder, @NotNull TyckDef def) {
    return getInstance(builder, AnyDef.fromVar(def.ref()));
  }

  public static @NotNull FreeJavaExpr getInstance(@NotNull FreeExprBuilder builder, @NotNull AnyDef def) {
    var desc = NameSerializer.getClassDesc(def);
    return builder.refField(builder.resolver().resolve(desc, AyaSerializer.STATIC_FIELD_INSTANCE, desc));
  }

  public static @NotNull FreeJavaExpr getRef(@NotNull FreeExprBuilder builder, @NotNull CallKind callType, @NotNull FreeJavaExpr call) {
    return builder.invoke(builder.resolver().resolve(
      callType.callType, AyaSerializer.FIELD_INSTANCE,
      callType.refType, ImmutableSeq.empty(), true
    ), call, ImmutableSeq.empty());
  }

  public final @NotNull FreeJavaExpr getCallInstance(@NotNull CallKind callType, @NotNull AnyDef def) {
    return builder.refField(builder.resolver().resolve(
      NameSerializer.getClassDesc(def),
      AyaSerializer.FIELD_EMPTYCALL,
      callType.callType)
    );
  }

  public static @NotNull ImmutableSeq<FreeJavaExpr> fromSeq(
    @NotNull FreeExprBuilder builder,
    @NotNull ClassDesc elementType,
    @NotNull FreeJavaExpr theSeq,
    int size
  ) {
    return ImmutableSeq.fill(size, idx -> makeSeqGet(builder, elementType, theSeq, idx));
  }

  public static @NotNull FreeJavaExpr makeSeqGet(
    @NotNull FreeExprBuilder builder,
    @NotNull ClassDesc elementType,
    @NotNull FreeJavaExpr theSeq,
    int size
  ) {
    var result = builder.invoke(Constants.SEQ_GET, theSeq, ImmutableSeq.of(builder.iconst(size)));
    return builder.checkcast(result, elementType);
  }

  /**
   * Actually perform serialization, unlike {@link #serialize}
   * which will perform some initialization after a {@code T} is obtained.
   */
  protected abstract @NotNull FreeJavaExpr doSerialize(@NotNull T term);

  /**
   * Prepare and perform {@link #doSerialize}
   */
  public abstract @NotNull FreeJavaExpr serialize(T unit);
}
