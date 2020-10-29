// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete;

import asia.kala.collection.mutable.Buffer;
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

  record Impossible() implements Clause {
  }
}
