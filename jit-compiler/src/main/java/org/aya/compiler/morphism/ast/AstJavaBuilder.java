// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ast;

import kala.collection.mutable.FreezableMutableList;
import org.aya.compiler.morphism.ClassBuilder;
import org.aya.compiler.morphism.JavaBuilder;
import org.aya.syntax.compile.AyaMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.function.Consumer;

public enum AstJavaBuilder implements JavaBuilder<AstDecl.Clazz> {
  INSTANCE;

  @Override public @NotNull AstDecl.Clazz buildClass(
    @Nullable AyaMetadata ayaMetadata,
    @NotNull ClassDesc className,
    @NotNull Class<?> superclass,
    @NotNull Consumer<ClassBuilder> builder) {
    var classBuilder = new AstClassBuilder(ayaMetadata, className, null, superclass, FreezableMutableList.create());
    builder.accept(classBuilder);
    return classBuilder.build();
  }
}
