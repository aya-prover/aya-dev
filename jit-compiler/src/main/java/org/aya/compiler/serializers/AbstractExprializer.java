// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.MethodRef;
import org.aya.compiler.morphism.*;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.TyckDef;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;

public abstract class AbstractExprializer<T> {
  protected final @NotNull ExprBuilder builder;
  protected final @NotNull SerializerContext context;

  protected AbstractExprializer(@NotNull ExprBuilder builder, @NotNull SerializerContext context) {
    this.builder = builder;
    this.context = context;
  }

  public @NotNull JavaExpr makeImmutableSeq(
    @NotNull Class<?> typeName,
    @NotNull ImmutableSeq<JavaExpr> terms
  ) {
    return makeImmutableSeq(builder, typeName, terms);
  }

  public @NotNull JavaExpr serializeToImmutableSeq(
    @NotNull Class<?> typeName,
    @NotNull ImmutableSeq<T> terms
  ) {
    var sered = terms.map(this::doSerialize);
    return makeImmutableSeq(typeName, sered);
  }

  public static @NotNull JavaExpr makeImmutableSeq(
    @NotNull ExprBuilder builder,
    @NotNull Class<?> typeName,
    @NotNull ImmutableSeq<JavaExpr> terms
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
  public static @NotNull JavaExpr makeImmutableSeq(
    @NotNull ExprBuilder builder,
    @NotNull MethodRef con,
    @NotNull Class<?> typeName,
    @NotNull ImmutableSeq<JavaExpr> terms
  ) {
    ImmutableSeq<JavaExpr> args;

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
      args = ImmutableSeq.of(builder.mkArray(AstUtil.fromClass(typeName), terms.size(), terms));
    }

    return builder.invoke(con, args);
  }

  /**
   * Return the reference to the {@code INSTANCE} field of the compiled class to {@param def}
   */
  public final @NotNull JavaExpr getInstance(@NotNull AnyDef def) {
    return getInstance(builder, def);
  }

  public static @NotNull JavaExpr getInstance(@NotNull ExprBuilder builder, @NotNull TyckDef def) {
    return getInstance(builder, AnyDef.fromVar(def.ref()));
  }

  public static @NotNull JavaExpr getInstance(@NotNull ExprBuilder builder, @NotNull ClassDesc desc) {
    return builder.refField(FreeJavaResolver.resolve(desc, AyaSerializer.STATIC_FIELD_INSTANCE, desc));
  }

  public static @NotNull JavaExpr getInstance(@NotNull ExprBuilder builder, @NotNull AnyDef def) {
    return getInstance(builder, NameSerializer.getClassDesc(def));
  }

  public static @NotNull JavaExpr getRef(@NotNull ExprBuilder builder, @NotNull CallKind callType, @NotNull JavaExpr call) {
    return builder.invoke(FreeJavaResolver.resolve(
      callType.callType, AyaSerializer.FIELD_INSTANCE,
      callType.refType, ImmutableSeq.empty(), true
    ), call, ImmutableSeq.empty());
  }

  public final @NotNull JavaExpr getCallInstance(@NotNull CallKind callType, @NotNull AnyDef def) {
    return builder.refField(FreeJavaResolver.resolve(
      NameSerializer.getClassDesc(def),
      AyaSerializer.FIELD_EMPTYCALL,
      callType.callType)
    );
  }

  public static @NotNull ImmutableSeq<JavaExpr> fromSeq(
    @NotNull ExprBuilder builder,
    @NotNull ClassDesc elementType,
    @NotNull JavaExpr theSeq,
    int size
  ) {
    return ImmutableSeq.fill(size, idx -> makeSeqGet(builder, elementType, theSeq, idx));
  }

  public static @NotNull JavaExpr makeSeqGet(
    @NotNull ExprBuilder builder,
    @NotNull ClassDesc elementType,
    @NotNull JavaExpr theSeq,
    int size
  ) {
    var result = builder.invoke(Constants.SEQ_GET, theSeq, ImmutableSeq.of(builder.iconst(size)));
    return builder.checkcast(result, elementType);
  }

  /**
   * Actually perform serialization, unlike {@link #serialize}
   * which will perform some initialization after a {@code T} is obtained.
   */
  protected abstract @NotNull JavaExpr doSerialize(@NotNull T term);

  /**
   * Prepare and perform {@link #doSerialize}
   */
  public abstract @NotNull JavaExpr serialize(T unit);

  public static @NotNull JavaExpr makeCallInvoke(
    @NotNull ExprBuilder builder,
    @NotNull MethodRef ref,
    @NotNull JavaExpr instance,
    @NotNull JavaExpr normalizer,
    @NotNull SeqView<JavaExpr> args
  ) {
    return builder.invoke(ref, instance, InvokeSignatureHelper.args(normalizer, args));
  }
}
