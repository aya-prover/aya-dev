// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.error;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public record WithPos<T>(@NotNull SourcePos sourcePos, T data) implements SourceNode {
  public static <U> WithPos<? extends U> narrow(@NotNull WithPos<U> value) { return value; }

  public static <T> @NotNull WithPos<T> dummy(@NotNull T data) {
    return new WithPos<>(SourcePos.NONE, data);
  }

  public <U> @Contract("_->new") WithPos<U> map(@NotNull Function<T, U> mapper) {
    return new WithPos<>(sourcePos, mapper.apply(data));
  }

  public @NotNull WithPos<T> update(@NotNull T data) {
    return data == this.data ? this : new WithPos<>(sourcePos, data);
  }

  public @NotNull WithPos<T> descent(@NotNull PosedUnaryOperator<T> f) { return update(f.apply(this)); }
  public <R> @NotNull WithPos<R> replace(@NotNull R value) { return new WithPos<>(sourcePos, value); }
}
