// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ast;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.FreezableMutableList;
import org.aya.compiler.FieldRef;
import org.aya.compiler.MethodRef;
import org.aya.compiler.morphism.ArgumentProvider;
import org.aya.compiler.morphism.CodeBuilder;
import org.aya.compiler.morphism.ExprBuilder;
import org.aya.compiler.morphism.JavaExpr;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.function.BiConsumer;

import static org.aya.compiler.morphism.ast.AstCodeBuilder.assertFreeExpr;

public enum AstExprBuilder implements ExprBuilder {
  INSTANCE;

  @Override public @NotNull JavaExpr mkNew(@NotNull MethodRef conRef, @NotNull ImmutableSeq<JavaExpr> args) {
    assert conRef.checkArguments(args);
    return new AstExpr.New(conRef, assertFreeExpr(args));
  }

  @Override
  public @NotNull JavaExpr invoke(@NotNull MethodRef method, @NotNull JavaExpr owner, @NotNull ImmutableSeq<JavaExpr> args) {
    assert method.checkArguments(args);
    return new AstExpr.Invoke(method, assertFreeExpr(owner), assertFreeExpr(args));
  }

  @Override public @NotNull JavaExpr invoke(@NotNull MethodRef method, @NotNull ImmutableSeq<JavaExpr> args) {
    assert method.checkArguments(args);
    return new AstExpr.Invoke(method, null, assertFreeExpr(args));
  }

  @Override public @NotNull JavaExpr refField(@NotNull FieldRef field) {
    return new AstExpr.RefField(field, null);
  }

  @Override public @NotNull JavaExpr refField(@NotNull FieldRef field, @NotNull JavaExpr owner) {
    return new AstExpr.RefField(field, assertFreeExpr(owner));
  }

  @Override public @NotNull JavaExpr refEnum(@NotNull ClassDesc enumClass, @NotNull String enumName) {
    return new AstExpr.RefEnum(enumClass, enumName);
  }

  @Override public @NotNull JavaExpr mkLambda(
    @NotNull ImmutableSeq<JavaExpr> captures,
    @NotNull MethodRef method,
    @NotNull BiConsumer<ArgumentProvider.Lambda, CodeBuilder> builder
  ) {
    var capturec = captures.size();
    var argc = method.paramTypes().size();
    // [0..captures.size()]th parameters are captures
    // [captures.size()..]th parameters are lambda arguments
    // Note that the [VariablePool] counts from 0,
    // as the arguments does NOT count as [local](AstVariable.Local) variables, but instead a [reference to the argument](AstVariable.Arg).
    var lambdaBodyBuilder = new AstCodeBuilder(FreezableMutableList.create(),
      new VariablePool(), false, false);
    builder.accept(new AstArgumentProvider.Lambda(capturec, argc), lambdaBodyBuilder);
    var lambdaBody = lambdaBodyBuilder.build();

    return new AstExpr.Lambda(assertFreeExpr(captures), method, lambdaBody);
  }
  @Override public @NotNull JavaExpr iconst(int i) { return new AstExpr.Iconst(i); }
  @Override public @NotNull JavaExpr iconst(boolean b) { return new AstExpr.Bconst(b); }
  @Override public @NotNull JavaExpr aconst(@NotNull String value) { return new AstExpr.Sconst(value); }
  @Override public @NotNull JavaExpr aconstNull(@NotNull ClassDesc type) { return new AstExpr.Null(type); }
  @Override public @NotNull JavaExpr thisRef() { return AstExpr.This.INSTANCE; }

  @Override
  public @NotNull JavaExpr mkArray(@NotNull ClassDesc type, int length, @Nullable ImmutableSeq<JavaExpr> initializer) {
    return new AstExpr.Array(type, length, initializer == null ? null : assertFreeExpr(initializer));
  }

  @Override public @NotNull JavaExpr getArray(@NotNull JavaExpr array, int index) {
    return new AstExpr.GetArray(assertFreeExpr(array), index);
  }

  @Override public @NotNull JavaExpr checkcast(@NotNull JavaExpr obj, @NotNull ClassDesc as) {
    return new AstExpr.CheckCast(assertFreeExpr(obj), as);
  }
}
