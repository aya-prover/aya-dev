// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism.free;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.data.FieldRef;
import org.aya.compiler.free.data.MethodRef;
import org.aya.syntax.compile.CompiledAya;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;

public sealed interface FreeDecl {
  record Clazz(
    @Nullable CompiledAya metadata,
    @NotNull ClassDesc name,
    @NotNull Class<?> superclass,
    @NotNull ImmutableSeq<FreeDecl> members
  ) implements FreeDecl { }

  // Constructors also count as method, with method name "<init>".
  record Method(
    @NotNull MethodRef signature,
    @NotNull ImmutableSeq<FreeStmt> body
  ) implements FreeDecl { }

  record ConstantField(@NotNull FieldRef signature, @NotNull FreeExpr init) implements FreeDecl { }
}
