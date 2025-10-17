// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ast;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.FieldRef;
import org.aya.compiler.MethodRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;

// TODO: consider [AstExpr#type]
public sealed interface AstExpr {
  record New(@NotNull MethodRef conRef, @NotNull ImmutableSeq<AstVariable> args) implements AstExpr { }
  // record RefCapture(int capture) implements AstExpr { }
  record Invoke(@NotNull MethodRef methodRef, @Nullable AstVariable owner,
                @NotNull ImmutableSeq<AstVariable> args) implements AstExpr { }
  record Ref(@NotNull AstVariable variable) implements AstExpr { }
  record RefField(@NotNull FieldRef fieldRef, @Nullable AstVariable owner) implements AstExpr { }
  record RefEnum(@NotNull ClassDesc enumClass, @NotNull String enumName) implements AstExpr { }
  record Lambda(@NotNull ImmutableSeq<AstVariable> captures, @NotNull MethodRef method,
                @NotNull ImmutableSeq<AstStmt> body) implements AstExpr { }

  sealed interface Const extends AstExpr { }
  record Iconst(int value) implements Const { }
  record Bconst(boolean value) implements Const { }
  record Sconst(@NotNull String value) implements Const { }
  record Null(@NotNull ClassDesc type) implements Const { }
  enum This implements AstExpr { INSTANCE }

  record Array(@NotNull ClassDesc type, int length,
               @Nullable ImmutableSeq<AstVariable> initializer) implements AstExpr { }
  record GetArray(@NotNull AstVariable array, int index) implements AstExpr { }
  record CheckCast(@NotNull AstVariable obj, @NotNull ClassDesc as) implements AstExpr { }
}
