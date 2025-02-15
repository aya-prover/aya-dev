// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.FieldRef;
import org.aya.compiler.MethodRef;
import org.aya.syntax.compile.AyaMetadata;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public interface ClassBuilder {
  static @NotNull MethodRef makeConstructorRef(@NotNull ClassDesc owner, @NotNull ImmutableSeq<ClassDesc> parameterTypes) {
    return new MethodRef(owner, ConstantDescs.INIT_NAME, ConstantDescs.CD_void, parameterTypes, false);
  }

  void buildNestedClass(
    @NotNull AyaMetadata ayaMetadata,
    @NotNull String name,
    @NotNull Class<?> superclass,
    @NotNull Consumer<ClassBuilder> builder
  );

  @NotNull MethodRef buildMethod(
    @NotNull ClassDesc returnType,
    @NotNull String name,
    @NotNull ImmutableSeq<ClassDesc> paramTypes,
    @NotNull BiConsumer<ArgumentProvider, CodeBuilder> builder
  );

  @NotNull MethodRef buildConstructor(
    @NotNull ImmutableSeq<ClassDesc> paramTypes,
    @NotNull BiConsumer<ArgumentProvider, CodeBuilder> builder
  );

  @NotNull FieldRef buildConstantField(
    @NotNull ClassDesc returnType,
    @NotNull String name,
    @NotNull Function<ExprBuilder, JavaExpr> initializer
  );
}
