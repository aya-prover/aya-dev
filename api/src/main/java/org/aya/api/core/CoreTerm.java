// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.core;

import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.aya.api.util.NormalizeMode;
import org.aya.pretty.doc.Docile;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author kiva, ice1000
 */
@ApiStatus.NonExtendable
public interface CoreTerm extends Docile {
  /** @return Number of usages of the given var. */
  int findUsages(@NotNull Var var);
  /** @return The synthesized type of the given term. */
  @NotNull CoreTerm computeType();
  /**
   * Perform a scope-check for a given term.
   *
   * @param allowed variables allowed in this term.
   * @return the variables in this term that are not allowed.
   */
  @NotNull Buffer<LocalVar> scopeCheck(@NotNull ImmutableSeq<LocalVar> allowed);
  @NotNull CoreTerm normalize(@NotNull NormalizeMode mode);
  @Nullable CorePat toPat();
}
