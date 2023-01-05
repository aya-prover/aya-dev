// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.visitor;

import org.aya.concrete.Pattern;
import org.jetbrains.annotations.NotNull;

public interface EndoPattern {
  default @NotNull Pattern pre(@NotNull Pattern pattern) {
    return pattern;
  }

  default @NotNull Pattern post(@NotNull Pattern pattern) {
    return pattern;
  }

  default Pattern apply(Pattern pattern) {
    return post(pre(pattern).descent(this::apply));
  }
}
