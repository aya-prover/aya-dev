package org.mzi.core.term;

import asia.kala.collection.Seq;
import asia.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public interface AppTerm extends Term {
  @NotNull Term function();
  @NotNull ImmutableSeq<@NotNull Arg> arguments();

  record Apply(
    @NotNull Term function,
    @NotNull Arg argument
  ) implements AppTerm {
    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitApp(this, p);
    }

    @Contract(" -> new")
    @Override public @NotNull ImmutableSeq<@NotNull Arg> arguments() {
      return ImmutableSeq.of(argument());
    }
  }
}
