// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.visitor;

import org.glavo.kala.Unit;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.Reporter;
import org.mzi.core.term.AppTerm;
import org.mzi.core.term.Term;
import org.mzi.tyck.ExprTycker;
import org.mzi.tyck.MetaContext;

/**
 * Instantiates holes (assuming all holes are solved).
 *
 * @author ice1000
 */
public final record Stripper(
  @NotNull MetaContext metaContext,
  @NotNull Reporter reporter
  ) implements TermFixpoint<Unit> {
  @Contract(pure = true) @Override public @NotNull Term visitHole(@NotNull AppTerm.HoleApp term, Unit emptyTuple) {
    var sol = metaContext.solutions().getOption(term);
    if (sol.isEmpty()) {
      // TODO[ice]: unsolved meta
      throw new ExprTycker.TyckerException();
    }
    return sol.get();
  }
}
