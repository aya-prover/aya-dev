package org.mzi.core.def;

import asia.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;
import org.mzi.core.ref.Bind;
import org.mzi.core.term.Term;

/**
 * @author ice1000
 */
public record FnDef(
  @NotNull String name,
  @NotNull ImmutableSeq<@NotNull Bind> binds,
  @NotNull Term body
) implements Def {
}
