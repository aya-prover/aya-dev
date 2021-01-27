// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.visitor;

import org.glavo.kala.Unit;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.core.term.AppTerm;
import org.mzi.core.term.Term;
import org.mzi.tyck.MetaContext;

/**
 * Instantiates holes (assuming all holes are solved).
 *
 * @author ice1000
 */
public final class Stripper implements TermFixpoint<Unit> {
  public static @NotNull Stripper INSTANCE(@NotNull MetaContext metaContext) {
    return new Stripper(metaContext);
  }

  private final @NotNull MetaContext metaContext;

  @Contract(pure = true) private Stripper(@NotNull MetaContext metaContext) {
    this.metaContext = metaContext;
  }

  @Contract(pure = true) @Override public @NotNull Term visitHole(@NotNull AppTerm.HoleApp term, Unit emptyTuple) {
    var sol = metaContext.solutions().getOption(term);
    // assuming all holes are solved
    assert sol.isDefined();
    return sol.get();
  }
}
