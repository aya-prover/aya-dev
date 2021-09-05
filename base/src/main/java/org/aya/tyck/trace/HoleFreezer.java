// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.trace;

import kala.tuple.Unit;
import org.aya.core.sort.Sort;
import org.aya.core.visitor.TermFixpoint;
import org.aya.tyck.unify.level.LevelEqnSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000
 */
public record HoleFreezer(@Nullable LevelEqnSet eqnSet) implements TermFixpoint<Unit> {
  @Override public Sort.@NotNull CoreLevel visitLevel(Sort.@NotNull CoreLevel sort, Unit unit) {
    return eqnSet != null ? eqnSet.applyTo(sort) : sort;
  }
}
