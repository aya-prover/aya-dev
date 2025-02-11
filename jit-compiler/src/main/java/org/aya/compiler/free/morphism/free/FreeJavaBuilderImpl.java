// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism.free;

import kala.collection.mutable.FreezableMutableList;
import org.aya.compiler.free.FreeClassBuilder;
import org.aya.compiler.free.FreeJavaBuilder;
import org.aya.syntax.compile.AyaMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.function.Consumer;

public enum FreeJavaBuilderImpl implements FreeJavaBuilder<FreeDecl.Clazz> {
  INSTANCE;

  @Override public @NotNull FreeDecl.Clazz buildClass(
    @Nullable AyaMetadata ayaMetadata,
    @NotNull ClassDesc className,
    @NotNull Class<?> superclass,
    @NotNull Consumer<FreeClassBuilder> builder) {
    var classBuilder = new FreeClassBuilderImpl(ayaMetadata, className, null, superclass, FreezableMutableList.create());
    builder.accept(classBuilder);
    return classBuilder.build();
  }
}
