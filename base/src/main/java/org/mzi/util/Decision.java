package org.mzi.util;

import org.jetbrains.annotations.NotNull;

public enum Decision {
  NO, MAYBE, YES;

  public @NotNull Decision max(Decision other) {
    return ordinal() >= other.ordinal() ? this : other;
  }

  public @NotNull Decision min(Decision other) {
    return ordinal() <= other.ordinal() ? this : other;
  }
}
