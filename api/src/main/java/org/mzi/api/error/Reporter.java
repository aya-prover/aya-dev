package org.mzi.api.error;

import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public interface Reporter {
  void report(@NotNull Error error);
}
