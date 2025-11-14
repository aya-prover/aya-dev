// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ir;

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

public sealed interface IrDecl {
  @Debug.Renderer(text = "className().toString()")
  record Clazz(
    @Nullable AyaMetadata metadata,
    @NotNull ClassDesc owner,
    @Nullable String nested,
    @NotNull Class<?> superclass,
    @NotNull ImmutableSeq<IrDecl> members
  ) implements IrDecl {
    public @NotNull ClassDesc className() {
      return nested != null ? owner.nested(nested) : owner;
    }
  }

  /// Constructors also count as method, with method name "<init>".
  @Debug.Renderer(text = "signature().name()")
  record Method(
    @NotNull MethodRef signature,
    boolean isStatic,
    @NotNull ImmutableSeq<IrStmt> body
  ) implements IrDecl, Docile {
    @Override public @NotNull Doc toDoc() {
      return Doc.vcat(signature.toDoc(),
        Doc.nest(2, Doc.vcat(body.view().map(IrStmt::toDoc))));
    }
  }

  /// Used for initializing constant, static fields
  record StaticInitBlock(@NotNull ImmutableSeq<IrStmt> body) implements IrDecl { }

  record ConstantField(@NotNull FieldRef signature) implements IrDecl { }
}
