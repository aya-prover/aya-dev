// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic.stmt;

import kala.collection.Seq;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

public interface Reducible {
  /**
   * @param fallback return this when unable to reduce, it is acceptable that fallback is null.
   * @return not null if reduce successfully, fallback if unable to reduce
   */
  Term invoke(Term fallback, @NotNull Seq<@NotNull Term> args);
}
