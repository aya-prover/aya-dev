// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @param isProp If this term is of a Prop-type.
 *               When working within Prop, there might be well-typed terms
 *               whose type is not a Prop-type.
 * @implNote non-Prop erased term can not appear in non-erased terms.
 */
public record ErasedTerm(@NotNull Term type, boolean isProp, @Nullable SourcePos sourcePos) implements Term {
  public ErasedTerm(@NotNull Term type) {
    this(type, false, null);
  }

  public ErasedTerm(@NotNull Term type, boolean isProp) {
    this(type, isProp, null);
  }
}
