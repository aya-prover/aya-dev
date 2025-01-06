// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.*;
import org.aya.compiler.free.data.MethodRef;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.TyckDef;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;

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

  /// @param con the factory function used for constructing an {@link ImmutableSeq}, usually it is {@link ImmutableSeq#of}.
  ///                       This function may re-resolve the factory function to fixed size parameters one
  ///                       with the name of {@param con}
  ///                       in case {@param terms} is very small.
  /// @see ImmutableSeq#empty()
  /// @see ImmutableSeq#of(Object)
  /// @see ImmutableSeq#of(Object, Object)
  /// @see ImmutableSeq#of(Object, Object, Object)
  /// @see ImmutableSeq#of(Object, Object, Object, Object)
  /// @see ImmutableSeq#of(Object, Object, Object, Object, Object)
  /// @see ImmutableSeq#of(Object[])
  public static @NotNull FreeJavaExpr makeImmutableSeq(
    @NotNull FreeExprBuilder builder,
    @NotNull MethodRef con,
    @NotNull Class<?> typeName,
    @NotNull ImmutableSeq<FreeJavaExpr> terms
  ) {
    ImmutableSeq<FreeJavaExpr> args;

    if (terms.size() <= 5) {
      String name = con.name();
      ImmutableSeq<ClassDesc> params;

      // re-resolve
      if (terms.isEmpty()) {
        name = Constants.NAME_EMPTY;
        params = ImmutableSeq.empty();
      } else {
        params = ImmutableSeq.fill(terms.size(), ConstantDescs.CD_Object);
      }

      con = FreeJavaResolver.resolve(
        con.owner(), name,
        con.returnType(), params,
        con.isInterface());

      args = terms;
    } else {
      args = ImmutableSeq.of(builder.mkArray(FreeUtil.fromClass(typeName), terms.size(), terms));
    }

    return builder.invoke(con, args);
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

  public static @NotNull FreeJavaExpr getInstance(@NotNull FreeExprBuilder builder, @NotNull ClassDesc desc) {
    return builder.refField(FreeJavaResolver.resolve(desc, AyaSerializer.STATIC_FIELD_INSTANCE, desc));
  }

  public static @NotNull FreeJavaExpr getInstance(@NotNull FreeExprBuilder builder, @NotNull AnyDef def) {
    return getInstance(builder, NameSerializer.getClassDesc(def));
  }

  public static @NotNull FreeJavaExpr getRef(@NotNull FreeExprBuilder builder, @NotNull CallKind callType, @NotNull FreeJavaExpr call) {
    return builder.invoke(FreeJavaResolver.resolve(
      callType.callType, AyaSerializer.FIELD_INSTANCE,
      callType.refType, ImmutableSeq.empty(), true
    ), call, ImmutableSeq.empty());
  }

  public final @NotNull FreeJavaExpr getCallInstance(@NotNull CallKind callType, @NotNull AnyDef def) {
    return builder.refField(FreeJavaResolver.resolve(
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
