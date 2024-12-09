// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.data.FieldData;
import org.aya.compiler.free.data.MethodData;
import org.aya.syntax.compile.CompiledAya;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface FreeClassBuilder {
  @NotNull FreeJavaResolver resolver();

  void buildNestedClass(
    CompiledAya compiledAya,
    @NotNull String name,
    @NotNull Class<?> superclass,
    @NotNull Consumer<FreeClassBuilder> builder
  );

  @NotNull MethodData buildMethod(
    @NotNull ClassDesc returnType,
    @NotNull String name,
    @NotNull ImmutableSeq<ClassDesc> paramTypes,
    @NotNull BiConsumer<ArgumentProvider, FreeCodeBuilder> builder
  );

  void buildConstructor(
    @NotNull ImmutableSeq<ClassDesc> superConParamTypes,
    @NotNull ImmutableSeq<FreeJava> superConArgs,
    @NotNull ImmutableSeq<ClassDesc> paramTypes,
    @NotNull BiConsumer<ArgumentProvider, FreeCodeBuilder> builder
  );

  @NotNull FieldData buildConstantField(
    @NotNull ClassDesc returnType,
    @NotNull String name
  );
}
