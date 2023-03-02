// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.error;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public record WithPos<T>(@NotNull SourcePos sourcePos, T data) {
  public <U> @Contract("_->new") WithPos<U> map(@NotNull Function<T, U> mapper) {
    return new WithPos<>(sourcePos, mapper.apply(data));
  }
}
