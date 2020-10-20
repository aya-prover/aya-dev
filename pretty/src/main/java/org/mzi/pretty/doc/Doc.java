package org.mzi.pretty.doc;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
public sealed interface Doc {
  /// --------------------------- Doc Variants

  /**
   * The empty document; conceptually the unit of 'Cat'
   */
  record Empty() implements Doc {
  }

  /**
   * A clickable line text without '\n' and a link to target.
   */
  record Text(@NotNull String text) implements Doc {
  }

  /**
   * Hard line break
   */
  record Line() implements Doc {
  }

  /**
   * Lay out the first 'Doc', but when flattened (via 'group'), prefer
   * the second.
   * The layout algorithms work under the assumption that the first
   * alternative is less wide than the flattened second alternative.
   */
  record FlatAlt(@NotNull Doc first, @NotNull Doc second) implements Doc {
  }

  /**
   * Concatenation of two documents
   */
  record Cat(@NotNull Doc first, @NotNull Doc second) implements Doc {
  }

  /**
   * Document indented by a number of columns
   */
  record Nest(int indent, @NotNull Doc doc) implements Doc {
  }

  /// --------------------------- DocFactory functions
  @Contract("-> new")
  static @NotNull Doc empty() {
    return new Empty();
  }

  /**
   * lays out the document {@param doc} with the current nesting level
   * (indentation of the following lines) increased by {@param indent}.
   * Negative values are allowed, and decrease the nesting level accordingly.
   *
   * @param indent indentation of the following lines
   * @param doc    the document to lay out
   * @return indented document
   */
  @Contract("_, _ -> new")
  static @NotNull Doc nest(int indent, @NotNull Doc doc) {
    return indent == 0
      ? doc
      : new Nest(indent, doc);
  }
}
