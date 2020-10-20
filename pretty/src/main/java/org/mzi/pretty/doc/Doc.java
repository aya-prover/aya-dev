package org.mzi.pretty.doc;

import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
public sealed interface Doc {
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
}
