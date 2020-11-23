// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.visitor;

import asia.kala.Unit;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.core.term.AppTerm;
import org.mzi.core.term.Term;

/**
 * Instantiates holes (assuming all holes are solved).
 *
 * @author ice1000
 */
public final class Stripper implements TermFixpoint<Unit> {
  public static final @NotNull Stripper INSTANCE = new Stripper();

  @Contract(pure = true) private Stripper() {
  }

  @Contract(pure = true) @Override public @NotNull Term visitHole(@NotNull AppTerm.HoleApp term, Unit emptyTuple) {
    return term.solution().get();
  }
}
