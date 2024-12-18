// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism.free;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.FreeJavaExpr;
import org.aya.compiler.free.data.FieldRef;
import org.aya.compiler.free.data.MethodRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;

public sealed interface FreeExpr extends FreeJavaExpr {
  record New(@NotNull MethodRef conRef, @NotNull ImmutableSeq<FreeExpr> args) implements FreeExpr { }
  record RefVariable(@NotNull FreeVariable var) implements FreeExpr { }
  record RefCapture(int capture) implements FreeExpr { }
  record Invoke(@NotNull MethodRef methodRef, @Nullable FreeExpr owner,
                @NotNull ImmutableSeq<FreeExpr> args) implements FreeExpr { }
  record RefField(@NotNull FieldRef fieldRef, @Nullable FreeExpr owner) implements FreeExpr { }
  record RefEnum(@NotNull ClassDesc enumClass, @NotNull String enumName) implements FreeExpr { }
  record Lambda(@NotNull ImmutableSeq<FreeExpr> captures, @NotNull MethodRef method,
                @NotNull ImmutableSeq<FreeStmt> body) implements FreeExpr { }

  sealed interface Const extends FreeExpr { }
  record Iconst(int value) implements Const { }
  record Bconst(boolean value) implements Const { }
  record Sconst(@NotNull String value) implements Const { }
  record Null(@NotNull ClassDesc type) implements Const { }
  enum This implements FreeExpr { INSTANCE }

  record Array(@NotNull ClassDesc type, int length,
               @Nullable ImmutableSeq<FreeExpr> initializer) implements FreeExpr { }
  record GetArray(@NotNull FreeExpr array, int index) implements FreeExpr { }
  record CheckCast(@NotNull FreeExpr obj, @NotNull ClassDesc as) implements FreeExpr { }
}
