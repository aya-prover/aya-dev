// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.serde;

import kala.collection.mutable.MutableMap;
import kala.tuple.Unit;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.core.sort.Sort;
import org.aya.core.term.CallTerm;
import org.aya.core.term.ErrorTerm;
import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public final class TermSerializer implements Term.Visitor<Unit, SerTerm> {
  public final @NotNull SerState state;

  public TermSerializer(@NotNull SerState state) {
    this.state = state;
  }

  public static record SerState(
    @NotNull MutableMap<Sort.LvlVar, Integer> levelCache,
    @NotNull MutableMap<LocalVar, Integer> localCache,
    @NotNull MutableMap<DefVar<?, ?>, Integer> defCache
  ) {
  }

  @Override public SerTerm visitError(@NotNull ErrorTerm term, Unit unit) {
    throw new AssertionError("Shall not have error term serialized.");
  }

  @Override public SerTerm visitHole(CallTerm.@NotNull Hole term, Unit unit) {
    throw new AssertionError("Shall not have holes serialized.");
  }
}
