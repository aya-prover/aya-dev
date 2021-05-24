// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.util;

import org.aya.api.error.SourcePos;
import org.aya.api.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public record WithPos<T>(@NotNull SourcePos sourcePos, T data) {
  public <U> WithPos<U> map(@NotNull Function<T, U> mapper) {
    return new WithPos<>(sourcePos, mapper.apply(data));
  }

  public static @NotNull LocalVar toVar(@NotNull WithPos<String> data) {
    return new LocalVar(data.data, data.sourcePos);
  }
}
