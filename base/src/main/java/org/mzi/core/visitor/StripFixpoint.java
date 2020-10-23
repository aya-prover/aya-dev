package org.mzi.core.visitor;

import asia.kala.EmptyTuple;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.core.term.HoleTerm;
import org.mzi.core.term.Term;

/**
 * Instantiates holes (assuming all holes are solved).
 *
 * @author ice1000
 */
public final class StripFixpoint implements TermFixpoint<EmptyTuple> {
  public static final @NotNull StripFixpoint INSTANCE = new StripFixpoint();

  @Contract(pure = true) private StripFixpoint() {
  }

  @Contract(pure = true) @Override public @NotNull Term visitHole(@NotNull HoleTerm holeTerm, EmptyTuple emptyTuple) {
    var value = holeTerm.solution().value;
    assert value != null;
    return value;
  }
}
