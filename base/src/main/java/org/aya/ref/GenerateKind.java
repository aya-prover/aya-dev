// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ref;

import org.aya.concrete.stmt.GeneralizedVar;
import org.jetbrains.annotations.NotNull;

public sealed interface GenerateKind {
  /**
   * Not generated
   */
  final class None implements GenerateKind {
    public static final @NotNull None INSTANCE = new None();

    private None() {}
  }

  final class Anonymous implements GenerateKind {
    public static final @NotNull Anonymous INSTANCE = new Anonymous();

    private Anonymous() {}
  }

  record Generalized(@NotNull GeneralizedVar origin) implements GenerateKind {
  }

  record Renamed(@NotNull LocalVar origin) implements GenerateKind {
  }
}
