package org.mzi.core.term;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author ice1000
 */
public interface AppTerm extends Term {
  @NotNull Term function();
  @NotNull List<@NotNull Arg> arguments();
}
