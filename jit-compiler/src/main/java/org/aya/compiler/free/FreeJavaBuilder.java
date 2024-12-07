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
  interface FreeClass {
    void buildNestedClass(
      CompiledAya compiledAya,
      @NotNull String name,
      @NotNull ClassDesc superclass,
      @NotNull Consumer<FreeClass> builder
    );

    void buildMethod(
      @NotNull ClassDesc returnType,
      @NotNull String name,
      @NotNull ImmutableSeq<ClassDesc> paramTypes,
      @NotNull Consumer<FreeCode> builder
    );

    void buildConstructor(
      @NotNull ImmutableSeq<ClassDesc> superConParamTypes,
      @NotNull ImmutableSeq<Consumer<FreeExpr>> superConArgs,
      @NotNull ImmutableSeq<ClassDesc> paramTypes,
      @NotNull Consumer<FreeClass> builder
    );
  }

  interface FreeCode {
    void makeVar(@NotNull String name, @Nullable Consumer<FreeExpr> initializer);
    void refVar(@NotNull String name);
  }

  interface FreeExpr {
    /**
     * A {@code new} expression, the class should have only one (public) constructor.
     */
    void newObject(@NotNull ClassDesc className, @NotNull ImmutableSeq<Consumer<FreeExpr>> args);
  }

  void buildClass(CompiledAya compiledAya, ClassDesc className, ClassDesc superclass, Consumer<FreeClass> builder);
}
