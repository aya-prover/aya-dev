package org.mzi.api.error;

import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public interface Error {
  enum Level {
    INFO, WARN_UNUSED {
      @Override
      public String toString() {
        return "WARN";
      }
    }, GOAL, WARN, ERROR
  }

  enum Stage {TERCK, TYCK, RESOLVE, PARSE, OTHER}

  @NotNull Level level();
  default @NotNull Stage stage() {
    return Stage.OTHER;
  }
}
