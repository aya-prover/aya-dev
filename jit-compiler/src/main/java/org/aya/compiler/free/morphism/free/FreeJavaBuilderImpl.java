// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism.free;

import kala.collection.mutable.FreezableMutableList;
import org.aya.compiler.free.*;
import org.aya.syntax.compile.CompiledAya;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.function.Consumer;

public enum FreeJavaBuilderImpl implements FreeJavaBuilder<FreeDecl.Clazz> {
  INSTANCE;

  @Override
  public @NotNull FreeDecl.Clazz buildClass(
    @Nullable CompiledAya compiledAya,
    @NotNull ClassDesc className,
    @NotNull Class<?> superclass,
    @NotNull Consumer<FreeClassBuilder> builder) {
    var classBuilder = new FreeClassBuilderImpl(compiledAya, className, null, superclass, FreezableMutableList.create());
    builder.accept(classBuilder);
    return classBuilder.build();
  }
}
