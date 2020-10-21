package org.mzi.util;

import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public enum CMP {
  GE, EQ, LE;

  public @NotNull CMP not() {
    return switch (this) {
      case GE -> LE;
      case EQ -> EQ;
      case LE -> GE;
    };
  }
}
