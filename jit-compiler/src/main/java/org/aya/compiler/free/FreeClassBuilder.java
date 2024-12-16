// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.data.FieldRef;
import org.aya.compiler.free.data.MethodRef;
import org.aya.syntax.compile.CompiledAya;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public interface FreeClassBuilder {
  static @NotNull MethodRef makeConstructorRef(@NotNull ClassDesc owner, @NotNull ImmutableSeq<ClassDesc> parameterTypes) {
    return new MethodRef.Default(owner, ConstantDescs.INIT_NAME, ConstantDescs.CD_void, parameterTypes, false);
  }

  void buildNestedClass(
    @Nullable CompiledAya compiledAya,
    @NotNull String name,
    @NotNull Class<?> superclass,
    @NotNull Consumer<FreeClassBuilder> builder
  );

  @NotNull MethodRef buildMethod(
    @NotNull ClassDesc returnType,
    @NotNull String name,
    @NotNull ImmutableSeq<ClassDesc> paramTypes,
    @NotNull BiConsumer<ArgumentProvider, FreeCodeBuilder> builder
  );

  @NotNull MethodRef buildConstructor(
    @NotNull ImmutableSeq<ClassDesc> paramTypes,
    @NotNull BiConsumer<ArgumentProvider, FreeCodeBuilder> builder
  );

  @NotNull FieldRef buildConstantField(
    @NotNull ClassDesc returnType,
    @NotNull String name,
    @NotNull Function<FreeExprBuilder, FreeJavaExpr> initializer
  );

  @NotNull FreeJavaResolver resolver();
}
