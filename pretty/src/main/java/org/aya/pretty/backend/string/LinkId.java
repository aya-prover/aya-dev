// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.string;

import kala.control.Either;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * @author imkiva
 */
public sealed interface LinkId extends Serializable {
  static @NotNull LinkId page(@NotNull String link) {
    return new AnotherPage(link);
  }

  static @NotNull LinkId loc(@NotNull String where) {
    return new LocalId(Either.left(where));
  }

  static @NotNull LinkId loc(int where) {
    return new LocalId(Either.right(where));
  }

  record AnotherPage(@NotNull String link) implements LinkId {
  }

  record LocalId(@NotNull Either<String, Integer> type) implements LinkId {
  }
}
