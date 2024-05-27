// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.ref;

import org.jetbrains.annotations.NotNull;

public sealed interface GenerateKind {
  enum Basic implements GenerateKind {
    /**
     * Not generated
     */
    None,
    Anonymous,
    /**
     * This LocalVar is generated for tyck, should not alive after tyck
     */
    Tyck,
  }

  record Generalized(@NotNull GeneralizedVar origin) implements GenerateKind { }
}
