package org.mzi.concrete.term;

import asia.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Ref;

/**
 * @author re-xyr
 */
public record Param(
  @NotNull Ref ref,
  @NotNull Expr type,
  boolean explicit
) {
  public interface Visitor<P, R> {
    R visitParams(@NotNull ImmutableSeq<Param> params, P p);
  }
  // TODO[xyr]: is it better to implement a Tele-like class?
}
