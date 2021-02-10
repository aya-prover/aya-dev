// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.generic;

import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
public sealed interface Clause<Term> {
  record Possible<Term>(
    @NotNull Buffer<Pat<Term>> patterns,
    @NotNull Term expr
  ) implements Clause<Term> {
  }

  record Impossible<Term>() implements Clause<Term> {
  }
}
