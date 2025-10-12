// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ast;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.FieldRef;
import org.aya.compiler.MethodRef;
import org.aya.syntax.compile.AyaMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;

public sealed interface AstDecl {
  record Clazz(
    @Nullable AyaMetadata metadata,
    @NotNull ClassDesc owner,
    @Nullable String nested,
    @NotNull Class<?> superclass,
    @NotNull ImmutableSeq<AstDecl> members
  ) implements AstDecl {
    public @NotNull ClassDesc className() {
      return nested != null ? owner.nested(nested) : owner;
    }
  }

  // Constructors also count as method, with method name "<init>".
  record Method(
    @NotNull MethodRef signature,
    @NotNull ImmutableSeq<AstStmt> body
  ) implements AstDecl { }

  record ConstantField(@NotNull FieldRef signature) implements AstDecl { }
}
