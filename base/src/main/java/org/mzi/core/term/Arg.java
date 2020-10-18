package org.mzi.core.term;

import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record Arg(
  @NotNull Term term,
  boolean isExplicit
) {
}
