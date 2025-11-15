// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.MethodRef;
import org.aya.compiler.morphism.Constants;
import org.aya.compiler.morphism.FreeJavaResolver;
import org.aya.compiler.morphism.JavaUtil;
import org.aya.compiler.morphism.ir.IrCodeBuilder;
import org.aya.compiler.morphism.ir.IrExpr;
import org.aya.compiler.morphism.ir.IrValue;
import org.aya.compiler.morphism.ir.IrVariable;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.TyckDef;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;

public abstract class AbstractExprSerializer<T> {
  protected final @NotNull IrCodeBuilder builder;
  protected final @NotNull SerializerContext context;

  protected AbstractExprSerializer(@NotNull IrCodeBuilder builder, @NotNull SerializerContext context) {
    this.builder = builder;
    this.context = context;
  }

  public @NotNull IrVariable makeImmutableSeq(
    @NotNull Class<?> typeName,
    @NotNull ImmutableSeq<IrValue> terms
  ) {
    return makeImmutableSeq(builder, typeName, terms);
  }

  public @NotNull IrVariable serializeToImmutableSeq(
    @NotNull Class<?> typeName,
    @NotNull ImmutableSeq<T> terms
  ) {
    var sered = terms.<IrValue>map(this::doSerialize);
    return makeImmutableSeq(typeName, sered);
  }

  public static @NotNull IrVariable makeImmutableSeq(
    @NotNull IrCodeBuilder builder,
    @NotNull Class<?> typeName,
    @NotNull ImmutableSeq<? extends IrValue> terms
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
  public static @NotNull IrVariable makeImmutableSeq(
    @NotNull IrCodeBuilder builder,
    @NotNull MethodRef con,
    @NotNull Class<?> typeName,
    @NotNull ImmutableSeq<? extends IrValue> terms
  ) {
    ImmutableSeq<IrValue> args;

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

      args = ImmutableSeq.narrow(terms);
    } else {
      var var = builder.bindExpr(
        JavaUtil.fromClass(typeName.arrayType()),
        new IrExpr.Array(JavaUtil.fromClass(typeName), terms.size(), ImmutableSeq.narrow(terms)));
      args = ImmutableSeq.of(var);
    }

    var invoke = new IrExpr.Invoke(con, null, args);
    return builder.bindExpr(con.returnType(), invoke);
  }

  /**
   * Return the reference to the {@code INSTANCE} field of the compiled class to {@param def}
   */
  public final @NotNull IrVariable getInstance(@NotNull AnyDef def) {
    return getInstance(builder, def);
  }

  public static @NotNull IrVariable getInstance(@NotNull IrCodeBuilder builder, @NotNull TyckDef def) {
    return getInstance(builder, AnyDef.fromVar(def.ref()));
  }

  public static @NotNull IrVariable getInstance(@NotNull IrCodeBuilder builder, @NotNull ClassDesc desc) {
    return builder.refField(FreeJavaResolver.resolve(desc, AyaSerializer.STATIC_FIELD_INSTANCE, desc));
  }

  public static @NotNull IrVariable getInstance(@NotNull IrCodeBuilder builder, @NotNull AnyDef def) {
    return getInstance(builder, NameSerializer.getClassDesc(def));
  }

  public static @NotNull IrVariable getRef(@NotNull IrCodeBuilder builder, @NotNull CallKind callType, @NotNull IrVariable call) {
    var invoke = new IrExpr.Invoke(FreeJavaResolver.resolve(
      callType.callType, AyaSerializer.FIELD_INSTANCE,
      callType.refType, ImmutableSeq.empty(), true
    ), call, ImmutableSeq.empty());

    return builder.bindExpr(invoke.methodRef().returnType(), invoke);
  }

  public final @NotNull IrVariable getCallInstance(@NotNull CallKind callType, @NotNull AnyDef def) {
    return builder.refField(FreeJavaResolver.resolve(
      NameSerializer.getClassDesc(def),
      AyaSerializer.FIELD_EMPTYCALL,
      callType.callType)
    );
  }

  public static @NotNull ImmutableSeq<IrVariable> fromSeq(
    @NotNull IrCodeBuilder builder,
    @NotNull ClassDesc elementType,
    @NotNull IrVariable theSeq,
    int size
  ) {
    return ImmutableSeq.fill(size, idx -> makeSeqGet(builder, elementType, theSeq, idx));
  }

  public static @NotNull IrVariable makeSeqGet(
    @NotNull IrCodeBuilder builder,
    @NotNull ClassDesc elementType,
    @NotNull IrVariable theSeq,
    int size
  ) {
    var result = new IrExpr.Invoke(Constants.SEQ_GET, theSeq, ImmutableSeq.of(new IrExpr.Iconst(size)));
    var cast = new IrExpr.CheckCast(builder.bindExpr(ConstantDescs.CD_Object, result), elementType);
    return builder.bindExpr(elementType, cast);
  }

  /**
   * Actually perform serialization, unlike {@link #serialize}
   * which will perform some initialization after a {@code T} is obtained.
   */
  protected abstract @NotNull IrVariable doSerialize(@NotNull T term);

  /**
   * Prepare and perform {@link #doSerialize}
   */
  public abstract @NotNull IrVariable serialize(T unit);

  public static @NotNull IrVariable makeCallInvoke(
    @NotNull IrCodeBuilder builder,
    @NotNull MethodRef ref,
    @NotNull IrVariable normalizer,
    @NotNull SeqView<IrValue> args
  ) {
    return builder.invoke(ref, null, InvokeSignatureHelper.args(normalizer, args));
  }
}
