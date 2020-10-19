package org.mzi.core.def;

import org.jetbrains.annotations.NotNull;
import org.mzi.core.tele.Tele;
import org.mzi.core.term.Term;

/**
 * @author ice1000
 */
public record FnDef(
  @NotNull String name,
  @NotNull Tele telescope,
  @NotNull Term body
) implements Def {
}
