package org.mzi.pretty.doc;

import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
sealed interface Flatten {
  record Flattened(@NotNull Doc flattenedDoc) implements Flatten {
  }

  record AlreadyFlat() implements Flatten {
  }

  record NeverFlat() implements Flatten {
  }

  static Flatten flatDoc(@NotNull Doc doc) {
    // TODO: flat doc
    return new NeverFlat();
  }
}
