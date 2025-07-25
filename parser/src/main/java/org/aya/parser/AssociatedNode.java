// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.parser;

import kala.value.MutableValue;
import org.aya.intellij.GenericNode;
import org.jetbrains.annotations.NotNull;

/// This [MutableValue] implementation is used for completion
public record AssociatedNode<T>(@NotNull MutableValue<T> delegate,
                                @NotNull GenericNode<?> node) implements MutableValue<T> {
  public AssociatedNode(@NotNull GenericNode<?> node) {
    this(MutableValue.create(), node);
  }

  @Override
  public void set(T value) {
    delegate.set(value);
  }

  @Override
  public T get() {
    return delegate.get();
  }
}
