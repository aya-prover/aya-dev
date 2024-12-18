// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism.free;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.FreezableMutableList;
import org.aya.compiler.free.*;
import org.aya.compiler.free.data.FieldRef;
import org.aya.compiler.free.data.MethodRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.function.BiConsumer;

import static org.aya.compiler.free.morphism.free.FreeCodeBuilderImpl.*;

public final class FreeExprBuilderImpl implements FreeExprBuilder {
  public static final @NotNull FreeExprBuilderImpl INSTANCE = new FreeExprBuilderImpl();

  private FreeExprBuilderImpl() { }

  @Override
  public @NotNull FreeJavaExpr mkNew(@NotNull MethodRef conRef, @NotNull ImmutableSeq<FreeJavaExpr> args) {
    return new FreeExpr.New(conRef, assertFreeExpr(args));
  }

  @Override
  public @NotNull FreeJavaExpr invoke(@NotNull MethodRef method, @NotNull FreeJavaExpr owner, @NotNull ImmutableSeq<FreeJavaExpr> args) {
    return new FreeExpr.Invoke(method, assertFreeExpr(owner), assertFreeExpr(args));
  }

  @Override
  public @NotNull FreeJavaExpr invoke(@NotNull MethodRef method, @NotNull ImmutableSeq<FreeJavaExpr> args) {
    return new FreeExpr.Invoke(method, null, assertFreeExpr(args));
  }

  @Override
  public @NotNull FreeJavaExpr refField(@NotNull FieldRef field) {
    return new FreeExpr.RefField(field, null);
  }

  @Override
  public @NotNull FreeJavaExpr refField(@NotNull FieldRef field, @NotNull FreeJavaExpr owner) {
    return new FreeExpr.RefField(field, assertFreeExpr(owner));
  }

  @Override
  public @NotNull FreeJavaExpr refEnum(@NotNull ClassDesc enumClass, @NotNull String enumName) {
    return new FreeExpr.RefEnum(enumClass, enumName);
  }

  @Override
  public @NotNull FreeJavaExpr mkLambda(
    @NotNull ImmutableSeq<FreeJavaExpr> captures,
    @NotNull MethodRef method,
    @NotNull BiConsumer<ArgumentProvider.Lambda, FreeCodeBuilder> builder
  ) {
    var capturec = captures.size();
    var argc = method.paramTypes().size();
    // [0..captures.size()]th parameters are captures
    // [captures.size()..]th parameters are lambda arguments
    var lambdaBodyBuilder = new FreeCodeBuilderImpl(FreezableMutableList.create(), new VariablePool(), false, false);
    builder.accept(new FreeArgumentProvider.Lambda(capturec, argc), lambdaBodyBuilder);
    var lambdaBody = lambdaBodyBuilder.build();

    return new FreeExpr.Lambda(assertFreeExpr(captures), method, lambdaBody);
  }

  @Override
  public @NotNull FreeJavaExpr iconst(int i) {
    return new FreeExpr.Iconst(i);
  }

  @Override
  public @NotNull FreeJavaExpr iconst(boolean b) {
    return new FreeExpr.Bconst(b);
  }

  @Override
  public @NotNull FreeJavaExpr aconst(@NotNull String value) {
    return new FreeExpr.Sconst(value);
  }

  @Override
  public @NotNull FreeJavaExpr aconstNull(@NotNull ClassDesc type) {
    return new FreeExpr.Null(type);
  }

  @Override
  public @NotNull FreeJavaExpr thisRef() {
    return FreeExpr.This.INSTANCE;
  }

  @Override
  public @NotNull FreeJavaExpr mkArray(@NotNull ClassDesc type, int length, @Nullable ImmutableSeq<FreeJavaExpr> initializer) {
    return new FreeExpr.Array(type, length, assertFreeExpr(initializer));
  }

  @Override
  public @NotNull FreeJavaExpr getArray(@NotNull FreeJavaExpr array, int index) {
    return new FreeExpr.GetArray(assertFreeExpr(array), index);
  }

  @Override
  public @NotNull FreeJavaExpr checkcast(@NotNull FreeJavaExpr obj, @NotNull ClassDesc as) {
    return new FreeExpr.CheckCast(assertFreeExpr(obj), as);
  }
}
