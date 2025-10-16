// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import org.aya.compiler.morphism.JavaUtil;
import org.aya.syntax.core.def.ConDefLike;
import org.aya.syntax.core.def.DataDefLike;
import org.aya.syntax.core.def.FnDefLike;
import org.aya.syntax.core.def.PrimDefLike;
import org.aya.syntax.core.term.call.*;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;

public enum CallKind {
  Fn(FnCall.class, FnDefLike.class),
  Data(DataCall.class, DataDefLike.class),
  Con(ConCallLike.class, ConDefLike.class),
  Prim(PrimCall.class, PrimDefLike.class);

  public final @NotNull ClassDesc callType;
  public final @NotNull ClassDesc refType;

  CallKind(@NotNull Class<?> callType, @NotNull Class<?> refType) {
    this.callType = JavaUtil.fromClass(callType);
    this.refType = JavaUtil.fromClass(refType);
  }

  public static @NotNull CallKind from(@NotNull Callable.Tele call) {
    return switch (call) {
      case FnCall _ -> CallKind.Fn;
      case ConCall _ -> CallKind.Con;
      case DataCall _ -> CallKind.Data;
      case PrimCall _ -> CallKind.Prim;
      default -> throw new UnsupportedOperationException("TODO");
    };
  }
}
