// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.compile.CompiledAya;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.function.Consumer;

public interface FreeJavaBuilder {
  interface ClassBuilder {
    void buildNestedClass(
      CompiledAya compiledAya,
      @NotNull String name,
      @NotNull ClassDesc superclass,
      @NotNull Consumer<ClassBuilder> builder
    );

    void buildMethod(
      @NotNull ClassDesc returnType,
      @NotNull String name,
      @NotNull ImmutableSeq<ClassDesc> paramTypes,
      @NotNull Consumer<CodeBuilder> builder
    );

    void buildConstructor(
      @NotNull ImmutableSeq<ClassDesc> superConParamTypes,
      @NotNull ImmutableSeq<Consumer<ExprBuilder>> superConArgs,
      @NotNull ImmutableSeq<ClassDesc> paramTypes,
      @NotNull Consumer<ClassBuilder> builder
    );
  }

  interface CodeBuilder {
    void makeVar(@NotNull String name, @Nullable FreeJava initializer);
    void refVar(@NotNull String name);

    void ifNotTrue(@NotNull FreeJava notTrue, @NotNull Consumer<CodeBuilder> thenBlock, @NotNull Consumer<CodeBuilder> elseBlock);
    void ifTrue(@NotNull FreeJava theTrue, @NotNull Consumer<CodeBuilder> thenBlock, @NotNull Consumer<CodeBuilder> elseBlock);
    void ifInstanceOf(@NotNull FreeJava lhs, @NotNull ClassDesc rhs, @NotNull Consumer<CodeBuilder> thenBlock, @NotNull Consumer<CodeBuilder> elseBlock);
  }

  interface ExprBuilder {
    /**
     * A {@code new} expression, the class should have only one (public) constructor.
     */
    @NotNull FreeJava newObject(@NotNull ClassDesc className, @NotNull ImmutableSeq<Consumer<ExprBuilder>> args);
  }

  void buildClass(CompiledAya compiledAya, ClassDesc className, ClassDesc superclass, Consumer<ClassBuilder> builder);
}
