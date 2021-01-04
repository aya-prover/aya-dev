// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete;

import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
public sealed interface Clause {
  record Possible(
    @NotNull Buffer<Pattern> patterns,
    @NotNull Expr expr
  ) implements Clause {
  }

  final class Impossible implements Clause {
    public static final @NotNull Impossible INSTANCE = new Impossible();

    @Contract(pure = true) private Impossible() {
    }
  }
}
