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

  /**
   * Flatten but does not report changes to caller.
   *
   * @param doc doc to flatten
   * @return flattened doc
   */
  private static @NotNull Doc flatten(@NotNull Doc doc) {
    if (doc instanceof Doc.FlatAlt alt) {
      return flatten(alt.preferWhenFlatten());

    } else if (doc instanceof Doc.Cat cat) {
      return new Doc.Cat(flatten(cat.first()), flatten(cat.second()));

    } else if (doc instanceof Doc.Nest nest) {
      return new Doc.Nest(nest.indent(), flatten(nest.doc()));

    } else if (doc instanceof Doc.Line) {
      return new Doc.Fail();

    } else if (doc instanceof Doc.Union u) {
      return flatten(u.first());

    } else {
      return doc;
    }
  }
}
