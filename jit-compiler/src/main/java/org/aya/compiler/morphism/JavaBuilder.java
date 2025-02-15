// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism;

import org.aya.syntax.compile.AyaMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public interface JavaBuilder<Carrier> {
  @NotNull Carrier buildClass(
    @Nullable AyaMetadata metadata,
    @NotNull ClassDesc className,
    @NotNull Class<?> superclass,
    @NotNull Consumer<ClassBuilder> builder
  );
}
