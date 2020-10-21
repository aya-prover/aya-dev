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
    if (doc instanceof Doc.FlatAlt alt) {
      return new Flattened(flatten(alt.preferWhenFlatten()));

    } else if (doc instanceof Doc.Line) {
      return new NeverFlat();

    } else if (doc instanceof Doc.Union u) {
      return new Flattened(u.shorterOne());

    } else if (doc instanceof Doc.Nest n) {
      var result = flatDoc(n.doc());
      if (result instanceof Flattened f) {
        return new Flattened(new Doc.Nest(n.indent(), f.flattenedDoc()));
      } else {
        return result;
      }

    } else if (doc instanceof Doc.Column c) {
      return new Flattened(new Doc.Column(
        i -> flatten(c.docBuilder().apply(i))
      ));

    } else if (doc instanceof Doc.Cat c) {
      return flatCat(c);

    } else if (doc instanceof Doc.Empty
      || doc instanceof Doc.PlainText
      || doc instanceof Doc.HyperText) {
      return new AlreadyFlat();

    } else if (doc instanceof Doc.Fail) {
      return new NeverFlat();
    }

    throw new IllegalStateException("unreachable");
  }

  private static @NotNull Flatten flatCat(@NotNull Doc.Cat cat) {
    var l = flatDoc(cat.first());
    var r = flatDoc(cat.second());

    if (l instanceof NeverFlat || r instanceof NeverFlat) {
      return new NeverFlat();
    } else if (l instanceof AlreadyFlat && r instanceof AlreadyFlat) {
      return new AlreadyFlat();
    }

    if (l instanceof Flattened x) {
      if (r instanceof Flattened y) {
        return new Flattened(new Doc.Cat(x.flattenedDoc(), y.flattenedDoc()));
      } else if (r instanceof AlreadyFlat) {
        return new Flattened(new Doc.Cat(x.flattenedDoc(), cat.second()));
      }
    } else if (l instanceof AlreadyFlat && r instanceof Flattened y) {
      return new Flattened(new Doc.Cat(cat.first(), y.flattenedDoc()));
    }

    throw new IllegalStateException("unreachable");
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
      return flatten(u.shorterOne());

    } else if (doc instanceof Doc.Column c) {
      return new Doc.Column(
        i -> flatten(c.docBuilder().apply(i))
      );

    } else {
      return doc;
    }
  }
}
