// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ast;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.FieldRef;
import org.aya.compiler.MethodRef;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.aya.syntax.compile.AyaMetadata;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;

public sealed interface AstDecl {
  @Debug.Renderer(text = "className().toString()")
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

  /// Constructors also count as method, with method name "<init>".
  @Debug.Renderer(text = "signature().name()")
  record Method(
    @NotNull MethodRef signature,
    @NotNull ImmutableSeq<AstStmt> body
  ) implements AstDecl, Docile {
    @Override public @NotNull Doc toDoc() {
      return Doc.vcat(signature.toDoc(),
        Doc.nest(2, Doc.vcat(body.view().map(AstStmt::toDoc))));
    }
  }

  /// Used for initializing constant, static fields
  record StaticInitBlock(@NotNull ImmutableSeq<AstStmt> body) implements AstDecl { }

  record ConstantField(@NotNull FieldRef signature) implements AstDecl { }
}
