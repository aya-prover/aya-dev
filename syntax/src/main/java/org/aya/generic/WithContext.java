// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import kala.value.MutableValue;
import org.aya.syntax.context.ContextView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface WithContext {
  @NotNull MutableValue<? extends ContextView> theContext();
  default @Nullable ContextView context() {
    return theContext().get();
  }
}
