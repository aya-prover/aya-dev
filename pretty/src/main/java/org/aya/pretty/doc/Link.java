// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.doc;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

/**
 * @author imkiva
 */
public sealed interface Link extends Serializable {
  static @NotNull Link page(@NotNull String link) {
    return new DirectLink(link);
  }

  static @NotNull Link cross(@NotNull ImmutableSeq<String> path, @Nullable LocalId location) {
    return new CrossLink(path, location);
  }

  static @NotNull LocalId loc(@NotNull String where) {
    return new LocalId(Either.left(where));
  }

  static @NotNull LocalId loc(int where) {
    return new LocalId(Either.right(where));
  }

  record DirectLink(@NotNull String link) implements Link {
  }

  record CrossLink(@NotNull ImmutableSeq<String> path, @Nullable LocalId location) implements Link {
  }

  record LocalId(@NotNull Either<String, Integer> type) implements Link {
  }
}
