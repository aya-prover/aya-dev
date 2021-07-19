// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.doc;

import kala.collection.Seq;
import kala.collection.SeqLike;
import org.aya.pretty.backend.html.DocHtmlPrinter;
import org.aya.pretty.backend.html.HtmlPrinterConfig;
import org.aya.pretty.backend.latex.DocTeXPrinter;
import org.aya.pretty.backend.latex.TeXPrinterConfig;
import org.aya.pretty.backend.string.StringLink;
import org.aya.pretty.backend.string.StringPrinter;
import org.aya.pretty.backend.string.StringPrinterConfig;
import org.aya.pretty.backend.string.style.DebugStylist;
import org.aya.pretty.printer.Printer;
import org.aya.pretty.printer.PrinterConfig;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.function.BinaryOperator;
import java.util.function.IntFunction;
import java.util.stream.Stream;

/**
 * This class reimplemented Haskell
 * <a href="https://hackage.haskell.org/package/prettyprinter-1.7.0/docs/src/Prettyprinter.Internal.html">
 * PrettyPrint library's Doc module</a>.
 *
 * @author kiva
 */
public sealed interface Doc extends Docile {
  @Override default @NotNull Doc toDoc() {
    return this;
  }

  //region Doc Member Functions

  default @NotNull String renderToString(@NotNull StringPrinterConfig config) {
    var printer = new StringPrinter<>();
    return this.render(printer, config);
  }

  default @NotNull String renderToHtml() {
    return renderToHtml(true);
  }

  default @NotNull String renderToHtml(boolean withHeader) {
    var printer = new DocHtmlPrinter();
    return this.render(printer, new HtmlPrinterConfig(withHeader));
  }

  default @NotNull String renderToTeX() {
    var printer = new DocTeXPrinter();
    return this.render(printer, new TeXPrinterConfig());
  }

  default <Out, Config extends PrinterConfig>
  @NotNull Out render(@NotNull Printer<Out, Config> printer,
                      @NotNull Config config) {
    return printer.render(config, this);
  }

  default @NotNull String renderWithPageWidth(int pageWidth) {
    var config = new StringPrinterConfig(DebugStylist.INSTANCE, pageWidth);
    return this.renderToString(config);
  }

  default @NotNull String debugRender() {
    return renderWithPageWidth(114514);
  }

  //endregion

  //region Doc Variants

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
   * A special symbol that may gets rendered in a special way
   *
   * @author ice1000
   */
  record SpecialSymbol(@NotNull String text) implements Doc {
  }

  /**
   * A clickable text line without '\n'.
   */
  record HyperLinked(@NotNull Doc doc, @NotNull Link link, @Nullable String id) implements Doc {
    @Override public String toString() {
      return doc.toString();
    }

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      return doc.equals(((HyperLinked) o).doc);
    }

    @Override public int hashCode() {
      return doc.hashCode();
    }
  }

  /**
   * Styled document
   */
  record Styled(@NotNull Seq<Style> styles, @NotNull Doc doc) implements Doc {
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
   * The first lines of first document should be shorter than the
   * first lines of the second one, so the layout algorithm can pick the one
   * that fits best. Used to implement layout alternatives for 'softline' and 'group'.
   */
  record Union(@NotNull Doc shorterOne, @NotNull Doc longerOne) implements Doc {
  }

  /**
   * A document that will react on the current cursor position.
   */
  record Column(@NotNull IntFunction<Doc> docBuilder) implements Doc {
  }

  /**
   * A document that will react on the current nest level.
   */
  record Nesting(@NotNull IntFunction<Doc> docBuilder) implements Doc {
  }

  /**
   * A document that will react on the page width.
   */
  record PageWidth(@NotNull IntFunction<Doc> docBuilder) implements Doc {
  }

  //endregion

  //region DocFactory functions
  static @NotNull Doc linkDef(@NotNull Doc doc, int hashCode) {
    return new HyperLinked(doc, new StringLink("#" + hashCode), String.valueOf(hashCode));
  }

  static @NotNull Doc linkRef(@NotNull Doc doc, int hashCode) {
    return new HyperLinked(doc, new StringLink("#" + hashCode), null);
  }

  static @NotNull Doc hyperLink(@NotNull Doc doc, @NotNull Link link) {
    return new HyperLinked(doc, link, null);
  }

  static @NotNull Doc hyperLink(@NotNull String plain, @NotNull Link link) {
    return hyperLink(Doc.plain(plain), link);
  }

  static @NotNull Doc styled(@NotNull Style style, @NotNull Doc doc) {
    return new Doc.Styled(Seq.of(style), doc);
  }

  static @NotNull Doc styled(@NotNull Style style, @NotNull String plain) {
    return new Doc.Styled(Seq.of(style), Doc.plain(plain));
  }

  static @NotNull Doc styled(@NotNull Styles builder, @NotNull Doc doc) {
    return new Doc.Styled(builder.styles, doc);
  }

  static @NotNull Doc styled(@NotNull Styles builder, @NotNull String plain) {
    return new Doc.Styled(builder.styles, Doc.plain(plain));
  }

  static @NotNull Doc wrap(String leftSymbol, String rightSymbol, Doc doc) {
    return Doc.cat(Doc.symbol(leftSymbol), doc, Doc.symbol(rightSymbol));
  }

  static @NotNull Doc braced(Doc doc) {
    return wrap("{", "}", doc);
  }
  static @NotNull Doc angled(Doc doc) {
    return wrap("<", ">", doc);
  }
  static @NotNull Doc parened(Doc doc) {
    return wrap("(", ")", doc);
  }

  /**
   * Return conditional {@link Doc#empty()}
   *
   * @param cond      condition
   * @param otherwise otherwise
   * @return {@link Empty} when {@code cond} is true, otherwise {@code otherwise}
   */
  @Contract("_, _ -> new")
  static @NotNull Doc emptyIf(boolean cond, Doc otherwise) {
    return cond ? empty() : otherwise;
  }

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
   * Layout a document depending on which column it starts at.
   * {@link Doc#align(Doc)} is implemented in terms of {@code column}.
   *
   * <pre>
   * >>> column (\l -> "Columns are" <+> pretty l <> "-based.")
   * Columns are 0-based.
   *
   * >>> let doc = "prefix" <+> column (\l -> "| <- column" <+> pretty l)
   * >>> vsep [indent n doc | n <- [0,4,8]]
   * prefix | <- column 7
   *     prefix | <- column 11
   *         prefix | <- column 15
   * </pre>
   *
   * @param docBuilder document generator when current position provided
   * @return column action document
   */
  @Contract("_ -> new")
  static @NotNull Doc column(@NotNull IntFunction<Doc> docBuilder) {
    return new Column(docBuilder);
  }

  /**
   * Layout a document depending on the current 'nest'ing level.
   * {@link Doc#align(Doc)} is implemented in terms of {@code nesting}.
   *
   * <pre>
   * >>> let doc = "prefix" <+> nesting (\l -> brackets ("Nested:" <+> pretty l))
   * >>> vsep [indent n doc | n <- [0,4,8]]
   * prefix [Nested: 0]
   *     prefix [Nested: 4]
   *         prefix [Nested: 8]
   * </pre>
   *
   * @param docBuilder document generator when current nest level provided
   * @return nest level action document
   */
  @Contract("_ -> new")
  static @NotNull Doc nesting(@NotNull IntFunction<Doc> docBuilder) {
    return new Nesting(docBuilder);
  }

  /**
   * Layout a document depending on the page width, if one has been specified.
   *
   * <pre>
   * >>> let prettyPageWidth (AvailablePerLine l r) = "Width:" <+> pretty l <> ", ribbon fraction:" <+> pretty r
   * >>> let doc = "prefix" <+> pageWidth (brackets . prettyPageWidth)
   * >>> putDocW 32 (vsep [indent n doc | n <- [0,4,8]])
   * prefix [Width: 32, ribbon fraction: 1.0]
   *     prefix [Width: 32, ribbon fraction: 1.0]
   *         prefix [Width: 32, ribbon fraction: 1.0]
   * </pre>
   *
   * @param docBuilder document generator when page width provided
   * @return page width action document
   */
  @Contract("_ -> new")
  static @NotNull Doc pageWidth(@NotNull IntFunction<Doc> docBuilder) {
    return new PageWidth(docBuilder);
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
   * align lays out the document {@param doc} with the nesting level set to the
   * current column. It is used for example to implement {@link Doc#hang(int, Doc)}.
   * <p>
   * As an example, we will put a document right above another one, regardless of
   * the current nesting level. Without 'align'ment, the second line is put simply
   * below everything we've had so far,
   *
   * <pre>
   * >>> "lorem" <+> vsep ["ipsum", "dolor"]
   * lorem ipsum
   * dolor
   * </pre>
   * <p>
   * If we add an 'align' to the mix, the @'vsep'@'s contents all start in the
   * same column,
   *
   * <pre>
   * >>> "lorem" <+> align (vsep ["ipsum", "dolor"])
   * lorem ipsum
   *       dolor
   * </pre>
   *
   * @param doc document to be aligned
   * @return aligned document
   */
  @Contract("_ -> new")
  static @NotNull Doc align(@NotNull Doc doc) {
    // note: nesting might be negative
    return column(k -> nesting(i -> nest(k - i, doc)));
  }

  /**
   * hang lays out the document {@param doc} with a nesting level set to the
   * /current column/ plus {@param deltaNest}.
   * Negative values are allowed, and decrease the nesting level accordingly.
   *
   * <pre>
   * >>> let doc = reflow "Indenting these words with hang"
   * >>> putDocW 24 ("prefix" <+> hang 4 doc)
   * prefix Indenting these
   *            words with
   *            hang
   * </pre>
   * <p>
   * This differs from {@link Doc#nest(int, Doc)}, which is based on
   * the /current nesting level/ plus {@code indent}.
   * When you're not sure, try the more efficient 'nest' first. In our
   * example, this would yield
   *
   * <pre>
   * >>> let doc = reflow "Indenting these words with nest"
   * >>> putDocW 24 ("prefix" <+> nest 4 doc)
   * prefix Indenting these
   *     words with nest
   * </pre>
   *
   * @param deltaNest change of nesting level, relative to the start of the first line
   * @param doc       document to indent
   * @return hang-ed document
   */
  @Contract("_, _ -> new")
  static @NotNull Doc hang(int deltaNest, @NotNull Doc doc) {
    return align(nest(deltaNest, doc));
  }

  /**
   * indent indents document {@param doc} by {@param indent} columns,
   * starting from the current cursor position.
   *
   * <pre>
   * >>> let doc = reflow "The indent function indents these words!"
   * >>> putDocW 24 ("prefix" <> indent 4 doc)
   * prefix    The indent
   *           function
   *           indents these
   *           words!
   * </pre>
   *
   * @param indent number of spaces to increase indentation by
   * @param doc    document
   * @return indented document
   */
  @Contract("_, _ -> new")
  static @NotNull Doc indent(int indent, @NotNull Doc doc) {
    return hang(indent, simpleCat(spaces(indent), doc));
  }

  @Contract("_ -> new")
  static @NotNull Doc ordinal(int n) {
    var m = n % 100;
    if (m >= 4 && m <= 20) return Doc.plain(n + "th");
    return Doc.plain(n + switch (n % 10) {
      case 1 -> "st";
      case 2 -> "nd";
      case 3 -> "rd";
      default -> "th";
    });
  }

  /**
   * Plain text document
   *
   * @param text text that may contain '\n'
   * @return text document of the whole text
   */
  @SuppressWarnings("OptionalGetWithoutIsPresent")
  @Contract("_ -> new") static @NotNull Doc plain(String text) {
    if (!text.contains("\n")) {
      return new PlainText(text);
    }

    return Arrays.stream(text.split("\n", -1))
      .map(Doc::plain)
      .reduce((x, y) -> simpleCat(x, hardLine(), y))
      .get(); // never null
  }

  /**
   * @param text '\n' not allowed!
   * @return special symbol
   */
  @Contract("_ -> new") static @NotNull Doc symbol(String text) {
    assert !text.contains("\n");
    return new SpecialSymbol(text);
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
  static @NotNull Doc cat(Doc @NotNull ... docs) {
    return simpleCat(docs);
  }

  /**
   * vcat vertically concatenates the documents {@param docs}. If it is
   * 'group'ed, the line breaks are removed.
   * In other words vcat is like vsep, with newlines removed instead of
   * replaced by 'space's.
   *
   * <pre>
   * >>> let docs = Util.words "lorem ipsum dolor"
   * >>> vcat docs
   * lorem
   * ipsum
   * dolor
   * >>> group (vcat docs)
   * loremipsumdolor
   * </pre>
   * <p>
   * Since 'group'ing a 'vcat' is rather common, 'cat' is a built-in shortcut for
   * it.
   *
   * @param docs documents to concat
   * @return concat document
   */
  @Contract("_ -> new")
  static @NotNull Doc vcat(Doc @NotNull ... docs) {
    return join(lineEmpty(), docs);
  }

  @Contract("_ -> new") static @NotNull Doc vcat(@NotNull Stream<@NotNull Doc> docs) {
    return join(lineEmpty(), docs);
  }

  @Contract("_ -> new") static @NotNull Doc vcat(@NotNull SeqLike<@NotNull Doc> docs) {
    if (docs.isEmpty()) return Doc.empty();
    return join(lineEmpty(), docs);
  }

  /**
   * hcat concatenates all documents docs horizontally without any spacing.
   *
   * @param docs documents to concat
   * @return concat document
   */
  @Contract("_ -> new")
  static @NotNull Doc hcat(Doc @NotNull ... docs) {
    return hcat(Seq.of(docs));
  }

  /**
   * hcat concatenates all documents docs horizontally without any spacing.
   *
   * @param docs documents to concat
   * @return concat document
   */
  @Contract("_ -> new")
  static @NotNull Doc hcat(@NotNull SeqLike<@NotNull Doc> docs) {
    return concatWith(Doc::simpleCat, docs);
  }

  /**
   * fillCat concatenates documents {@param docs} horizontally with simpleCat() as
   * long as it fits the page, then inserts a 'line' and continues doing that
   * for all documents in {@param docs}. This is similar to how an ordinary word processor
   * lays out the text if you just keep typing after you hit the maximum line
   * length. ('line' means that if 'group'ed, the documents are separated with nothing
   * instead of newlines. See 'fillSep' if you want a 'space' instead.)
   * <p>
   * Observe the difference between 'fillSep' and 'fillCat'. 'fillSep'
   * concatenates the entries 'space'd when 'group'ed,
   *
   * <pre>
   *
   * >>> let docs = take 20 (cycle (["lorem", "ipsum", "dolor", "sit", "amet"]))
   * >>> putDocW 40 ("Grouped:" <+> group (fillSep docs))
   * Grouped: lorem ipsum dolor sit amet
   * lorem ipsum dolor sit amet lorem ipsum
   * dolor sit amet lorem ipsum dolor sit
   * amet
   *
   * On the other hand, 'fillCat' concatenates the entries directly when
   * 'group'ed,
   *
   * >>> putDocW 40 ("Grouped:" <+> group (fillCat docs))
   * Grouped: loremipsumdolorsitametlorem
   * ipsumdolorsitametloremipsumdolorsitamet
   * loremipsumdolorsitamet
   *
   * </pre>
   *
   * @param docs documents to concat
   * @return concat document
   */
  @Contract("_ -> new")
  static @NotNull Doc fillCat(Doc @NotNull ... docs) {
    return join(new FlatAlt(plain(" "), empty()), docs);
  }

  /**
   * sep tries laying out the documents {@param docs} separated with 'space's,
   * and if this does not fit the page, separates them with newlines. This is what
   * differentiates it from 'vsep', which always lays out its contents beneath
   * each other.
   *
   * <pre>
   * >>> let doc = "prefix" <+> sep ["text", "to", "lay", "out"]
   * >>> putDocW 80 doc
   * prefix text to lay out
   * </pre>
   * <p>
   * With a narrower layout, the entries are separated by newlines:
   *
   * <pre>
   * >>> putDocW 20 doc
   * prefix text
   * to
   * lay
   * out
   * </pre>
   *
   * @param docs documents to separate
   * @return separated documents
   */
  @Contract("_ -> new")
  static @NotNull Doc sep(Doc @NotNull ... docs) {
    return group(vsep(docs));
  }

  /**
   * vsep concatenates all documents {@param docs} above each other. If a
   * 'group' undoes the line breaks inserted by vsep, the documents are
   * separated with a 'space' instead.
   * <p>
   * Using 'vsep' alone yields
   *
   * <pre>
   * >>> "prefix" <+> vsep ["text", "to", "lay", "out"]
   * prefix text
   * to
   * lay
   * out
   * </pre>
   * <p>
   * 'group'ing a 'vsep' separates the documents with a 'space' if it fits the
   * page (and does nothing otherwise). See the {@link Doc#sep(Doc...)} convenience
   * function for this use case.
   * <p>
   * The 'align' function can be used to align the documents under their first
   * element:
   *
   * <pre>
   * >>> "prefix" <+> align (vsep ["text", "to", "lay", "out"])
   * prefix text
   *        to
   *        lay
   *        out
   * </pre>
   * <p>
   * Since 'group'ing a 'vsep' is rather common, 'sep' is a built-in for doing
   * that.
   *
   * @param docs documents to separate
   * @return separated documents
   */
  @Contract("_ -> new")
  static @NotNull Doc vsep(Doc @NotNull ... docs) {
    return join(line(), docs);
  }

  /**
   * hsep concatenates all documents {@param docs} horizontally with a space,
   * i.e. it puts a space between all entries.
   *
   * <pre>
   * >>> let docs = Util.words "lorem ipsum dolor sit amet"
   *
   * >>> hsep docs
   * lorem ipsum dolor sit amet
   *
   * </pre>
   * <p>
   * hsep does not introduce line breaks on its own, even when the page is too
   * narrow:
   *
   * <pre>
   * >>> putDocW 5 (hsep docs)
   * lorem ipsum dolor sit amet
   * </pre>
   *
   * @param docs documents to separate
   * @return separated documents
   */
  @Contract("_ -> new")
  static @NotNull Doc hsep(Doc @NotNull ... docs) {
    return hsep(Seq.of(docs));
  }

  /**
   * hsep concatenates all documents {@param docs} horizontally with a space,
   * i.e. it puts a space between all entries.
   *
   * <pre>
   * >>> let docs = Util.words "lorem ipsum dolor sit amet"
   *
   * >>> hsep docs
   * lorem ipsum dolor sit amet
   *
   * </pre>
   * <p>
   * hsep does not introduce line breaks on its own, even when the page is too
   * narrow:
   *
   * <pre>
   * >>> putDocW 5 (hsep docs)
   * lorem ipsum dolor sit amet
   * </pre>
   *
   * @param docs documents to separate
   * @return separated documents
   */
  @Contract("_ -> new")
  static @NotNull Doc hsep(@NotNull SeqLike<@NotNull Doc> docs) {
    return join(Doc.plain(" "), docs);
  }

  /**
   * fillSep concatenates the documents {@param docs} horizontally with a space
   * as long as it fits the page, then inserts a 'line' and continues doing that
   * for all documents in {@param docs}. ('line' means that if 'group'ed, the documents
   * are separated with a 'space' instead of newlines. Use {@link Doc#fillCat}
   * if you do not want a 'space'.
   * <p>
   * Let's print some words to fill the line:
   *
   * <pre>
   *
   * >>> let docs = take 20 (cycle ["lorem", "ipsum", "dolor", "sit", "amet"])
   * >>> putDocW 80 ("Docs:" <+> fillSep docs)
   * Docs: lorem ipsum dolor sit amet lorem ipsum dolor sit amet lorem ipsum dolor
   * sit amet lorem ipsum dolor sit amet
   *
   * The same document, printed at a width of only 40, yields
   *
   * >>> putDocW 40 ("Docs:" <+> fillSep docs)
   * Docs: lorem ipsum dolor sit amet lorem
   * ipsum dolor sit amet lorem ipsum dolor
   * sit amet lorem ipsum dolor sit amet
   *
   * </pre>
   *
   * @param docs documents to separate
   * @return separated documents
   */
  @Contract("_ -> new")
  static @NotNull Doc fillSep(Doc @NotNull ... docs) {
    return join(new FlatAlt(plain(" "), new Line()), docs);
  }

  @Contract("_, _ -> new")
  static @NotNull Doc join(@NotNull Doc delim, Doc @NotNull ... docs) {
    return join(delim, Seq.of(docs));
  }

  @Contract("_, _ -> new")
  static @NotNull Doc join(@NotNull Doc delim, Stream<Doc> docs) {
    return join(delim, docs.collect(Seq.factory()));
  }

  @Contract("_, _ -> new")
  static @NotNull Doc join(@NotNull Doc delim, @NotNull SeqLike<@NotNull Doc> docs) {
    return concatWith(
      (x, y) -> simpleCat(x, delim, y),
      docs
    );
  }

  /**
   * softline behaves like a {@code spaces(1)} if the resulting output fits the page,
   * otherwise like a {@code line()}.
   * <p>
   * For example, here, we have enough space to put everything in one line:
   *
   * <pre>
   * >>> let doc = "lorem ipsum" <> softline <> "dolor sit amet"
   * >>> putDocW 80 doc
   * lorem ipsum dolor sit amet
   * </pre>
   * If we narrow the page to width 10, the layouter produces a line break:
   * <pre>
   * >>> putDocW 10 doc
   * lorem ipsum
   * dolor sit amet
   * </pre>
   *
   * @return soft line document
   */
  @Contract("-> new")
  static @NotNull Doc softLine() {
    return new Union(line(), plain(" "));
  }

  /**
   * Another softLine but result nothing on page when flattened.
   *
   * @return soft line document
   */
  @Contract("-> new")
  static @NotNull Doc softLineEmpty() {
    return new Union(line(), empty());
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
   * Another version of line() but result nothing on page when flattened.
   *
   * @return line document
   */
  @Contract("-> new")
  static @NotNull Doc lineEmpty() {
    return new FlatAlt(new Line(), empty());
  }

  /**
   * Insert a number of spaces. Negative values count as 0.
   *
   * @param count count of spaces
   * @return space document
   */
  @Contract("_ -> new")
  static @NotNull Doc spaces(int count) {
    return count < 0
      ? empty()
      : plain(" ".repeat(count));
  }

  //endregion

  //region utility functions

  private static @NotNull Doc concatWith(@NotNull BinaryOperator<Doc> f, @NotNull SeqLike<@NotNull Doc> xs) {
    assert xs.size() > 0;
    if (xs.size() == 1) {
      return xs.get(0);
    }
    return xs.reduce(f); // never null
  }

  private static @NotNull Doc simpleCat(Doc @NotNull ... xs) {
    return concatWith(Doc::makeCat, Seq.of(xs));
  }

  private static @NotNull Doc simpleSpacedCat(Doc @NotNull ... xs) {
    return concatWith(
      (first, second) ->
        makeCat(
          first,
          second,
          (a, b) -> simpleCat(a, plain(" "), b)
        ),
      Seq.of(xs)
    );
  }

  private static @NotNull Doc makeCat(@NotNull Doc first, @NotNull Doc second) {
    return makeCat(first, second, Cat::new);
  }

  private static @NotNull Doc
  makeCat(@NotNull Doc first, @NotNull Doc second, @NotNull BinaryOperator<Doc> maker) {
    if (first instanceof Empty) return second;
    if (second instanceof Empty) return first;
    return maker.apply(first, second);
  }

  //endregion

  //region Internal implementation of groups

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

      } else if (doc instanceof Doc.Nesting c) {
        return new Flattened(new Doc.Nesting(
          i -> flatten(c.docBuilder().apply(i))
        ));

      } else if (doc instanceof Doc.PageWidth c) {
        return new Flattened(new Doc.PageWidth(
          i -> flatten(c.docBuilder().apply(i))
        ));

      } else if (doc instanceof Doc.Cat c) {
        return flatCat(c);

      } else if (doc instanceof Doc.Empty
        || doc instanceof Doc.PlainText
        || doc instanceof Doc.SpecialSymbol
        || doc instanceof Doc.HyperLinked
        || doc instanceof Doc.Styled) {
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

      } else if (doc instanceof Doc.Nesting n) {
        return new Doc.Nesting(
          i -> flatten(n.docBuilder().apply(i))
        );

      } else if (doc instanceof Doc.PageWidth n) {
        return new Doc.PageWidth(
          i -> flatten(n.docBuilder().apply(i))
        );

      } else {
        return doc;
      }
    }
  }
  //endregion
}
