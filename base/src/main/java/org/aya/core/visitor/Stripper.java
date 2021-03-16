// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.visitor;

import org.aya.core.term.CallTerm;
import org.aya.core.term.Term;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.MetaContext;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Instantiates holes (assuming all holes are solved).
 *
 * @author ice1000
 */
public final record Stripper(@NotNull MetaContext metaContext) implements TermFixpoint<Unit> {
  @Contract(pure = true) @Override public @NotNull Term visitHole(@NotNull CallTerm.Hole term, Unit emptyTuple) {
    var sol = metaContext.solutions().getOption(term);
    if (sol.isEmpty()) {
      // TODO[ice]: unsolved meta
      throw new ExprTycker.TyckerException();
    }
    return sol.get();
  }
}
