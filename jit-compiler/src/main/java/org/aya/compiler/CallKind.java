// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import org.aya.compiler.free.FreeUtils;
import org.aya.syntax.core.def.FnDefLike;
import org.aya.syntax.core.term.call.Callable;
import org.aya.syntax.core.term.call.FnCall;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;

public enum CallKind {
  Fn(FnCall.class, FnDefLike.class);

  public final @NotNull ClassDesc callType;
  public final @NotNull ClassDesc refType;

  CallKind(@NotNull Class<?> callType, @NotNull Class<?> refType) {
    this.callType = FreeUtils.fromClass(callType);
    this.refType = FreeUtils.fromClass(refType);
  }

  public static @NotNull CallKind from(@NotNull Callable call) {
    throw new UnsupportedOperationException("TODO");
  }
}
