package org.mzi.pretty.doc;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.function.BinaryOperator;

/**
 * This class reimplemented Haskel
 * <a href="https://hackage.haskell.org/package/prettyprinter-1.7.0/docs/src/Prettyprinter.Internal.html">
 * PrettyPrint library's Doc module</a>.
 *
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
   * Fail to flatten; used in {@link Flatten#flatDoc(Doc)}
   */
  record Fail() implements Doc {
  }

  /**
   * A plain text line without '\n'.
   */
  record PlainText(@NotNull String text) implements Doc {
  }

  /**
   * A clickable text line without '\n'.
   */
  record HyperText(@NotNull String text, @NotNull Link link) implements Doc {
  }

  /**
   * Hard line break
   */
  record Line() implements Doc {
  }

  /**
   * Lay out the defaultDoc 'Doc', but when flattened (via 'group'), prefer
   * the preferWhenFlatten.
   * The layout algorithms work under the assumption that the defaultDoc
   * alternative is less wide than the flattened preferWhenFlatten alternative.
   */
  record FlatAlt(@NotNull Doc defaultDoc, @NotNull Doc preferWhenFlatten) implements Doc {
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

  /**
   * The defaultDoc lines of defaultDoc document should be longer than the
   * defaultDoc lines of the preferWhenFlatten one, so the layout algorithm can pick the one
   * that fits best. Used to implement layout alternatives for 'softline' and 'group'.
   */
  record Union(@NotNull Doc first, @NotNull Doc second) implements Doc {
  }

  /// --------------------------- DocFactory functions

  /**
   * The empty document; conceptually the unit of 'Cat'
   *
   * @return empty document
   */
  @Contract("-> new")
  static @NotNull Doc empty() {
    return new Empty();
  }

  /**
   * By default, flatAlt renders as {@param defaultDoc}. However when 'group'ed,
   * {@param preferWhenFlattened} will be preferred, with {@param defaultDoc} as
   * the fallback for the case when {@param preferWhenFlattened} doesn't fit.
   *
   * @param defaultDoc          default document
   * @param preferWhenFlattened document selected when flattened
   * @return alternative document
   */
  @Contract("_, _ -> new")
  static @NotNull Doc flatAlt(@NotNull Doc defaultDoc, @NotNull Doc preferWhenFlattened) {
    return new FlatAlt(defaultDoc, preferWhenFlattened);
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

  /**
   * Plain text document
   *
   * @param text text that may contain '\n'
   * @return text document of the whole text
   */
  @Contract("_ -> new")
  static @NotNull Doc plain(String text) {
    if (!text.contains("\n")) {
      return new PlainText(text);
    }

    return Arrays.stream(text.split("\n"))
      .map(Doc::plain)
      .reduce(empty(), (x, y) -> simpleCat(simpleCat(x, line()), y));
  }

  /**
   * group tries laying out {@param doc} into a single line by removing the
   * contained line breaks; if this does not fit the page, or when a 'hardline'
   * within {@param doc} prevents it from being flattened, {@param doc} is laid out
   * without any changes.
   *
   * @param doc doc to flat
   * @return flatten document
   */
  @Contract("_ -> new")
  static @NotNull Doc group(@NotNull Doc doc) {
    if (doc instanceof Union) {
      return doc;
    } else if (doc instanceof FlatAlt alt) {
      var flattenResult = Flatten.flatDoc(alt.preferWhenFlatten());
      if (flattenResult instanceof Flatten.Flattened f) {
        return new Union(f.flattenedDoc(), alt.defaultDoc());
      } else if (flattenResult instanceof Flatten.AlreadyFlat) {
        return new Union(alt.preferWhenFlatten(), alt.defaultDoc());
      } else {
        return alt.defaultDoc();
      }
    } else {
      var flattenResult = Flatten.flatDoc(doc);
      if (flattenResult instanceof Flatten.Flattened f) {
        return new Union(f.flattenedDoc(), doc);
      } else {
        return doc;
      }
    }
  }

  /**
   * cat tries laying out the documents {@param docs} separated with nothing,
   * and if this does not fit the page, separates them with newlines. This is what
   * differentiates it from 'vcat', which always lays out its contents beneath
   * each other.
   *
   * @param docs documents to concat
   * @return cat document
   */
  @Contract("_ -> new")
  static @NotNull Doc cat(@NotNull Doc... docs) {
    return group(vcat(docs));
  }

  /**
   * vcat vertically concatenates the documents {@param docs}. If it is
   * 'group'ed, the line breaks are removed.
   * <p>
   * In other words vcat is like vsep, with newlines removed instead of
   * replaced by 'space's.
   * <p>
   * >>> let docs = Util.words "lorem ipsum dolor"
   * >>> vcat docs
   * lorem
   * ipsum
   * dolor
   * >>> group (vcat docs)
   * loremipsumdolor
   * <p>
   * Since 'group'ing a 'vcat' is rather common, 'cat' is a built-in shortcut for
   * it.
   *
   * @param docs documents to concat
   * @return concat document
   */
  @Contract("_ -> new")
  static @NotNull Doc vcat(@NotNull Doc... docs) {
    return concatWith(
      (x, y) -> simpleCat(simpleCat(x, lineEmpty()), y),
      docs
    );
  }

  /**
   * hcat concatenates all documents docs horizontally without any spacing.
   *
   * @param docs documents to concat
   * @return concat document
   */
  @Contract("_ -> new")
  static @NotNull Doc hcat(@NotNull Doc... docs) {
    return concatWith(Doc::simpleCat, docs);
  }

  private static @NotNull Doc concatWith(@NotNull BinaryOperator<Doc> f, @NotNull Doc... docs) {
    return Arrays.stream(docs).reduce(empty(), f);
  }

  private static @NotNull Doc simpleCat(@NotNull Doc a, @NotNull Doc b) {
    if (a instanceof Empty) {
      return b;
    }
    if (b instanceof Empty) {
      return a;
    }
    return new Cat(a, b);
  }

  /**
   * softline behaves like a {@code space()} if the resulting output fits the page,
   * otherwise like a {@code line()}.
   * <p>
   * For example, here, we have enough space to put everything in one line:
   * <p>
   * >>> let doc = "lorem ipsum" <> softline <> "dolor sit amet"
   * >>> putDocW 80 doc
   * lorem ipsum dolor sit amet
   * <p>
   * If we narrow the page to width 10, the layouter produces a line break:
   * <p>
   * >>> putDocW 10 doc
   * lorem ipsum
   * dolor sit amet
   *
   * @return soft line document
   */
  @Contract("-> new")
  static @NotNull Doc softLine() {
    return new Union(plain(" "), line());
  }

  /**
   * Another softLine but result nothing on page when 'group'ed.
   *
   * @return soft line document
   */
  @Contract("-> new")
  private static @NotNull Doc softLineEmpty() {
    return new Union(empty(), line());
  }

  /**
   * Unconditionally line break
   *
   * @return hard line document
   */
  @Contract("-> new")
  static @NotNull Doc hardLine() {
    return new Line();
  }

  /**
   * The line document advances to the next line
   * and indents to the current nesting level.
   *
   * @return line document
   */
  @Contract("-> new")
  static @NotNull Doc line() {
    return new FlatAlt(new Line(), plain(" "));
  }

  /**
   * Another version of line() but result nothing on page when 'group'ed.
   *
   * @return line document
   */
  @Contract("-> new")
  private static @NotNull Doc lineEmpty() {
    return new FlatAlt(new Line(), empty());
  }
}
