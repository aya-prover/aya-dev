// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.ref;

import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record QName(@NotNull QPath module, @NotNull String name) {
  public QName(@NotNull DefVar<?, ?> ref) {
    this(Objects.requireNonNull(ref.module), ref.name());
  }

  public ImmutableSeq<String> asStringSeq() {
    return module.module().module().appended(name);
  }
}
