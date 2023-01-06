// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.meta;

import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Constraints on a meta variable.
 *
 * @author ice1000
 */
public sealed interface MetaInfo {
  @Nullable Term result();
  /**
   * The type of the meta is known.
   * We shall check the solution against this type.
   */
  record Result(@NotNull Term result) implements MetaInfo {}

  /**
   * The meta variable is a type.
   * It should be able to appear on the RHS of a judgment.
   */
  record AnyType() implements MetaInfo {
    @Override public @Nullable Term result() {
      return null;
    }
  }
}
