package org.mzi.util;

import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public enum Ordering {
  Gt, Eq, Lt;

  /**
   * Invert ordering.
   *
   * @return {@link Ordering#Gt} when {@code this} is {@link Ordering#Lt} and vice versa
   * but nothing changes when {@code this} is {@link Ordering#Eq},
   */
  public @NotNull Ordering invert() {
    return switch (this) {
      case Gt -> Lt;
      case Eq -> Eq;
      case Lt -> Gt;
    };
  }
}
