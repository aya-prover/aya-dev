// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ast;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.FieldRef;
import org.aya.compiler.MethodRef;
import org.aya.compiler.morphism.JavaExpr;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;

public sealed interface AstExpr extends JavaExpr {
  record New(@NotNull MethodRef conRef, @NotNull ImmutableSeq<AstExpr> args) implements AstExpr { }
  record RefVariable(@NotNull AstVariable var) implements AstExpr { }
  record RefCapture(int capture) implements AstExpr { }
  record Invoke(@NotNull MethodRef methodRef, @Nullable AstExpr owner,
                @NotNull ImmutableSeq<AstExpr> args) implements AstExpr { }
  record RefField(@NotNull FieldRef fieldRef, @Nullable AstExpr owner) implements AstExpr { }
  record RefEnum(@NotNull ClassDesc enumClass, @NotNull String enumName) implements AstExpr { }
  record Lambda(@NotNull ImmutableSeq<AstExpr> captures, @NotNull MethodRef method,
                @NotNull ImmutableSeq<AstStmt> body) implements AstExpr { }

  sealed interface Const extends AstExpr { }
  record Iconst(int value) implements Const { }
  record Bconst(boolean value) implements Const { }
  record Sconst(@NotNull String value) implements Const { }
  record Null(@NotNull ClassDesc type) implements Const { }
  enum This implements AstExpr { INSTANCE }

  record Array(@NotNull ClassDesc type, int length,
               @Nullable ImmutableSeq<AstExpr> initializer) implements AstExpr { }
  record GetArray(@NotNull AstExpr array, int index) implements AstExpr { }
  record CheckCast(@NotNull AstExpr obj, @NotNull ClassDesc as) implements AstExpr { }
}
