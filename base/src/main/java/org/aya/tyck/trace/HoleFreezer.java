// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.trace;

import kala.tuple.Unit;
import org.aya.core.sort.Sort;
import org.aya.core.term.CallTerm;
import org.aya.core.term.ErrorTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.TermFixpoint;
import org.aya.pretty.doc.Doc;
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

  @Override public @NotNull Term visitHole(CallTerm.@NotNull Hole term, Unit unit) {
    var solution = term.ref().core().body;
    if (solution == null) return new ErrorTerm(Doc.plain("{??}"), false);
    return new ErrorTerm(options -> Doc.wrap("{?", "?}", solution.toDoc(options)), false);
  }
}
