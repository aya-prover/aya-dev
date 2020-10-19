package org.mzi.core.term;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.List;

/**
 * @author ice1000
 */
public interface AppTerm extends Term {
  @NotNull Term function();
  @NotNull List<@NotNull Arg> arguments();
  record TermAppTerm(
    @NotNull Term function,
    @NotNull Arg argument
  ) implements AppTerm {
    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitApp(this, p);
    }

    @Contract(" -> new")
    @Override public @NotNull @Unmodifiable List<@NotNull Arg> arguments() {
      return Collections.singletonList(argument());
    }
  }
}
