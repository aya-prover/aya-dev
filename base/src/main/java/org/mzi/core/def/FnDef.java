package org.mzi.core.def;

import org.jetbrains.annotations.NotNull;
import org.mzi.core.ref.Bind;
import org.mzi.core.term.Term;

import java.util.List;

/**
 * @author ice1000
 */
public record FnDef(
  @NotNull String name,
  @NotNull List<@NotNull Bind> binds,
  @NotNull Term body
) implements Def {
}
