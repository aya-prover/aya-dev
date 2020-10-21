package org.mzi.core.def;

import org.jetbrains.annotations.NotNull;
import org.mzi.core.tele.Tele;
import org.mzi.core.tele.Telescopic;
import org.mzi.core.term.Term;
import org.mzi.ref.DefRef;

/**
 * @author ice1000
 */
public final class FnDef implements Def, Telescopic {
  public final @NotNull DefRef ref;
  public final @NotNull Tele telescope;
  public final @NotNull Term result;
  public final @NotNull Term body;

  public FnDef(@NotNull String name, @NotNull Tele telescope, @NotNull Term result, @NotNull Term body) {
    this.ref = new DefRef(this, name);
    this.telescope = telescope;
    this.result = result;
    this.body = body;
  }

  @Override public @NotNull DefRef ref() {
    return ref;
  }

  @Override public @NotNull Tele telescope() {
    return telescope;
  }
}
