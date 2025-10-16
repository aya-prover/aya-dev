// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.MethodRef;
import org.aya.compiler.morphism.*;
import org.aya.compiler.morphism.ast.AstCodeBuilder;
import org.aya.compiler.morphism.ast.AstExpr;
import org.aya.compiler.morphism.ast.AstVariable;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.TyckDef;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;

public abstract class AbstractExprializer<T> {
  protected final @NotNull AstCodeBuilder builder;
  protected final @NotNull SerializerContext context;

  protected AbstractExprializer(@NotNull AstCodeBuilder builder, @NotNull SerializerContext context) {
    this.builder = builder;
    this.context = context;
  }

  public @NotNull AstVariable makeImmutableSeq(
    @NotNull Class<?> typeName,
    @NotNull ImmutableSeq<AstVariable> terms
  ) {
    return makeImmutableSeq(builder, typeName, terms);
  }

  public @NotNull AstVariable serializeToImmutableSeq(
    @NotNull Class<?> typeName,
    @NotNull ImmutableSeq<T> terms
  ) {
    var sered = terms.map(this::doSerialize);
    return makeImmutableSeq(typeName, sered);
  }

  public static @NotNull AstVariable makeImmutableSeq(
    @NotNull AstCodeBuilder builder,
    @NotNull Class<?> typeName,
    @NotNull ImmutableSeq<AstVariable> terms
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
  public static @NotNull AstVariable makeImmutableSeq(
    @NotNull AstCodeBuilder builder,
    @NotNull MethodRef con,
    @NotNull Class<?> typeName,
    @NotNull ImmutableSeq<AstVariable> terms
  ) {
    ImmutableSeq<AstVariable> args;

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
      var var = builder.bindExpr(new AstExpr.Array(AstUtil.fromClass(typeName), terms.size(), terms));
      args = ImmutableSeq.of(var);
    }

    var invoke = new AstExpr.Invoke(con, null, args);
    return builder.bindExpr(invoke);
  }

  /**
   * Return the reference to the {@code INSTANCE} field of the compiled class to {@param def}
   */
  public final @NotNull AstVariable getInstance(@NotNull AnyDef def) {
    return getInstance(builder, def);
  }

  public static @NotNull AstVariable getInstance(@NotNull AstCodeBuilder builder, @NotNull TyckDef def) {
    return getInstance(builder, AnyDef.fromVar(def.ref()));
  }

  public static @NotNull AstVariable getInstance(@NotNull AstCodeBuilder builder, @NotNull ClassDesc desc) {
    return builder.refField(FreeJavaResolver.resolve(desc, AyaSerializer.STATIC_FIELD_INSTANCE, desc));
  }

  public static @NotNull AstVariable getInstance(@NotNull AstCodeBuilder builder, @NotNull AnyDef def) {
    return getInstance(builder, NameSerializer.getClassDesc(def));
  }

  public static @NotNull AstVariable getRef(@NotNull AstCodeBuilder builder, @NotNull CallKind callType, @NotNull AstVariable call) {
    var invoke = new AstExpr.Invoke(FreeJavaResolver.resolve(
      callType.callType, AyaSerializer.FIELD_INSTANCE,
      callType.refType, ImmutableSeq.empty(), true
    ), call, ImmutableSeq.empty());

    return builder.bindExpr(invoke);
  }

  public final @NotNull AstVariable getCallInstance(@NotNull CallKind callType, @NotNull AnyDef def) {
    return builder.refField(FreeJavaResolver.resolve(
      NameSerializer.getClassDesc(def),
      AyaSerializer.FIELD_EMPTYCALL,
      callType.callType)
    );
  }

  public static @NotNull ImmutableSeq<AstVariable> fromSeq(
    @NotNull AstCodeBuilder builder,
    @NotNull ClassDesc elementType,
    @NotNull AstVariable theSeq,
    int size
  ) {
    return ImmutableSeq.fill(size, idx -> makeSeqGet(builder, elementType, theSeq, idx));
  }

  public static @NotNull AstVariable makeSeqGet(
    @NotNull AstCodeBuilder builder,
    @NotNull ClassDesc elementType,
    @NotNull AstVariable theSeq,
    int size
  ) {
    var result = new AstExpr.Invoke(Constants.SEQ_GET, theSeq, ImmutableSeq.of(builder.iconst(size)));
    var cast = new AstExpr.CheckCast(builder.bindExpr(result), elementType);
    return builder.bindExpr(cast);
  }

  /**
   * Actually perform serialization, unlike {@link #serialize}
   * which will perform some initialization after a {@code T} is obtained.
   */
  protected abstract @NotNull AstVariable doSerialize(@NotNull T term);

  /**
   * Prepare and perform {@link #doSerialize}
   */
  public abstract @NotNull AstVariable serialize(T unit);

  public static @NotNull AstExpr makeCallInvoke(
    @NotNull MethodRef ref,
    @NotNull AstVariable instance,
    @NotNull AstVariable normalizer,
    @NotNull SeqView<AstVariable> args
  ) {

    return new AstExpr.Invoke(ref, instance, InvokeSignatureHelper.args(normalizer, args));
  }
}
