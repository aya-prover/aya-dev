// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ref;

import org.aya.concrete.stmt.GeneralizedVar;
import org.jetbrains.annotations.NotNull;

public sealed interface GenerateKind {
  /**
   * Not generated
   */
  enum None implements GenerateKind {
    INSTANCE;
  }

  enum Anonymous implements GenerateKind {
    INSTANCE;
  }

  record Generalized(@NotNull GeneralizedVar origin) implements GenerateKind {
  }

  record Renamed(@NotNull LocalVar origin) implements GenerateKind {
  }
}
