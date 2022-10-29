// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.visitor;

import org.aya.concrete.Pattern;
import org.jetbrains.annotations.NotNull;

public interface PatternConsumer {
  default void pre(@NotNull Pattern pat) {}

  default void post(@NotNull Pattern pat) {}

  default void accept(@NotNull Pattern pat) {
    pre(pat);
    pat.descent(p -> {
      accept(p);
      return p;
    });
    post(pat);
  }
}
