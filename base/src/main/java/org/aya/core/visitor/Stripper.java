// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.visitor;

import org.aya.core.term.CallTerm;
import org.aya.core.term.Term;
import org.aya.tyck.ExprTycker;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Instantiates holes (assuming all holes are solved).
 *
 * @author ice1000
 */
public final class Stripper implements TermFixpoint<Unit> {
  @Contract(pure = true) @Override public @NotNull Term visitHole(@NotNull CallTerm.Hole term, Unit unit) {
    var sol = term.ref().core();
    if (sol.body == null) {
      // TODO[ice]: unsolved meta
      throw new ExprTycker.TyckerException();
    }
    return sol.body.accept(this, Unit.unit());
  }
}
