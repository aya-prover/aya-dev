// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.doc;

import kala.collection.Seq;
import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.pretty.backend.html.DocHtmlPrinter;
import org.aya.pretty.backend.html.Html5Stylist;
import org.aya.pretty.backend.latex.DocTeXPrinter;
import org.aya.pretty.backend.md.DocMdPrinter;
import org.aya.pretty.backend.md.MdStylist;
import org.aya.pretty.backend.string.DebugStylist;
import org.aya.pretty.backend.string.StringPrinter;
import org.aya.pretty.backend.string.StringPrinterConfig;
import org.aya.pretty.backend.terminal.AdaptiveCliStylist;
import org.aya.pretty.backend.terminal.DocTermPrinter;
import org.aya.pretty.printer.Printer;
import org.aya.pretty.printer.PrinterConfig;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntFunction;
import java.util.function.Supplier;

import static org.aya.pretty.printer.PrinterConfig.INFINITE_SIZE;

/**
 * This class reimplemented Haskell
 * <a href="https://hackage.haskell.org/package/prettyprinter-1.7.0/docs/src/Prettyprinter.Internal.html">
 * PrettyPrint library's Doc module</a>.
 *
 * @author kiva
 */
public sealed interface Doc extends Docile {
  @NotNull Doc ONE_WS = plain(" ");
  @NotNull Doc ALT_WS = flatAlt(ONE_WS, line());
  @NotNull Doc COMMA = cat(plain(","), ALT_WS);
  default boolean isNotEmpty() {
    return !isEmpty();
  }
  default boolean isEmpty() {
    return this instanceof Empty;
  }
  @Override default @NotNull Doc toDoc() {
    return this;
  }

  //region Doc APIs
  default @NotNull String renderToString(@NotNull StringPrinterConfig<?> config) {
    return render(new StringPrinter<>(), config);
  }

  default @NotNull String renderToString(int pageWidth, boolean unicode) {
    var config = new StringPrinterConfig<>(DebugStylist.DEFAULT);
    config.set(PrinterConfig.PageOptions.PageWidth, pageWidth);
    config.set(StringPrinterConfig.TextOptions.Unicode, unicode);
    return renderToString(config);
  }

  default @NotNull String renderToTerminal() {
    return renderToTerminal(INFINITE_SIZE, true);
  }

  default @NotNull String renderToTerminal(int pageWidth, boolean unicode) {
    var config = new DocTermPrinter.Config(AdaptiveCliStylist.INSTANCE);
    config.set(PrinterConfig.PageOptions.PageWidth, pageWidth);
    config.set(StringPrinterConfig.TextOptions.Unicode, unicode);
    return render(new DocTermPrinter(), config);
  }

  default @NotNull String renderToHtml() {
    return renderToHtml(true);
  }

  default @NotNull String renderToHtml(boolean withHeader) {
    var config = new DocHtmlPrinter.Config(Html5Stylist.DEFAULT);
    config.set(StringPrinterConfig.StyleOptions.HeaderCode, withHeader);
    config.set(StringPrinterConfig.StyleOptions.StyleCode, withHeader);
    config.set(StringPrinterConfig.StyleOptions.SeparateStyle, withHeader);
    config.set(StringPrinterConfig.TextOptions.Unicode, true);
    return render(new DocHtmlPrinter<>(), config);
  }

  default @NotNull String renderToMd() {
    return render(new DocMdPrinter(), new DocMdPrinter.Config(MdStylist.DEFAULT));
  }

  default @NotNull String renderToAyaMd() {
    var config = new DocMdPrinter.Config(MdStylist.DEFAULT);
    config.set(StringPrinterConfig.StyleOptions.AyaFlavored, true);
    config.set(StringPrinterConfig.StyleOptions.HeaderCode, true);
    config.set(StringPrinterConfig.StyleOptions.StyleCode, true);
    config.set(StringPrinterConfig.StyleOptions.SeparateStyle, true);
    config.set(StringPrinterConfig.TextOptions.Unicode, true);
    return render(new DocMdPrinter(), config);
  }

  default @NotNull String renderToTeX() {
    return render(new DocTeXPrinter(), new DocTeXPrinter.Config());
  }

  default <Out, Config extends PrinterConfig>
  @NotNull Out render(@NotNull Printer<Out, Config> printer, @NotNull Config config) {
    return printer.render(config, this);
  }

  /** Produce ASCII and infinite-width output */
  default @NotNull String debugRender() {
    return renderToString(INFINITE_SIZE, false);
  }

  /** Produce unicode and 80-width output */
  default @NotNull String commonRender() {
    return renderToString(80, true);
  }

  //endregion

  //region Doc Variants

  /** The empty document; conceptually the unit of 'Cat' */
  enum Empty implements Doc { INSTANCE }

  /**
   * A plain text line without '\n'. which may be escaped by backend.
   */
  record PlainText(@NotNull String text) implements Doc { }

  /**
   * Already escaped text, which will not be escaped by backend.
   * Callers should be responsible for escaping the text (like '\n').
   */
  record EscapedText(@NotNull String text) implements Doc { }

  /**
   * A special symbol that may get rendered in a special way
   */
  record SpecialSymbol(@NotNull String text) implements Doc { }

  /**
   * A clickable text line without '\n'.
   *
   * @param id   The id of the doc itself.
   * @param href The id of jump target when clicked.
   */
  record HyperLinked(
    @NotNull Doc doc, @NotNull Link href,
    @Nullable Link id, @Nullable String hover
  ) implements Doc { }

  record Image(@NotNull Doc alt, @NotNull Link src) implements Doc { }

  /** Inline code, with special escape settings compared to {@link PlainText} */
  record InlineCode(@NotNull Language language, @NotNull Doc code) implements Doc { }

  /** Code block, with special escape settings compared to {@link PlainText} */
  record CodeBlock(@NotNull Language language, @NotNull Doc code) implements Doc { }

  /** Inline math, with special escape settings compared to {@link PlainText} */
  record InlineMath(@NotNull Doc formula) implements Doc { }

  /** Math block, with special escape settings compared to {@link PlainText} */
  record MathBlock(@NotNull Doc formula) implements Doc { }

  /**
   * Styled document
   */
  record Styled(@NotNull Seq<Style> styles, @NotNull Doc doc) implements Doc { }
  record Tooltip(@NotNull Doc doc, @NotNull Docile tooltip) implements Doc { }

  /**
   * Hard line break
   */
  enum Line implements Doc {
    INSTANCE
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
  record Cat(@NotNull ImmutableSeq<Doc> inner) implements Doc {
  }

  record List(boolean isOrdered, @NotNull ImmutableSeq<Doc> items) implements Doc {
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
  static @NotNull Doc linkDef(@NotNull Doc doc, @NotNull Link id) {
    return linkDef(doc, id, null);
  }

  static @NotNull Doc linkRef(@NotNull Doc doc, @NotNull Link href) {
    return linkRef(doc, href, null);
  }

  static @NotNull Doc linkDef(@NotNull Doc doc, @NotNull Link id, @Nullable String hover) {
    return new HyperLinked(doc, id, id, hover);
  }

  static @NotNull Doc linkRef(@NotNull Doc doc, @NotNull Link href, @Nullable String hover) {
    return new HyperLinked(doc, href, null, hover);
  }

  static @NotNull Doc hyperLink(@NotNull Doc doc, @NotNull Link href, @Nullable String hover) {
    return new HyperLinked(doc, href, null, hover);
  }

  static @NotNull Doc hyperLink(@NotNull String plain, @NotNull Link href) {
    return hyperLink(plain(plain), href, null);
  }

  static @NotNull Doc image(@NotNull Doc alt, @NotNull Link src) {
    return new Image(alt, src);
  }

  static @NotNull Doc code(@NotNull String code) {
    return code(Language.Builtin.Aya, plain(code));
  }

  static @NotNull Doc code(@NotNull Doc code) {
    return code(Language.Builtin.Aya, code);
  }

  static @NotNull Doc code(@NotNull Language language, @NotNull Doc code) {
    return new InlineCode(language, code);
  }

  static @NotNull Doc codeBlock(@NotNull Language language, @NotNull Doc code) {
    return new CodeBlock(language, code);
  }

  static @NotNull Doc codeBlock(@NotNull Language language, @NotNull String code) {
    return codeBlock(language, plain(code));
  }

  static @NotNull Doc math(@NotNull Doc formula) {
    return new InlineMath(formula);
  }

  static @NotNull Doc mathBlock(@NotNull Doc formula) {
    return new MathBlock(formula);
  }

  static @NotNull Doc styled(@NotNull Style style, @NotNull Doc doc) {
    return new Styled(Seq.of(style), doc);
  }

  static @NotNull Doc styled(@NotNull Style style, @NotNull String plain) {
    return new Styled(Seq.of(style), plain(plain));
  }

  static @NotNull Doc styled(@NotNull Styles builder, @NotNull Doc doc) {
    return new Styled(builder.styles(), doc);
  }

  static @NotNull Doc styled(@NotNull Styles builder, @NotNull String plain) {
    return new Styled(builder.styles(), plain(plain));
  }

  static @NotNull Doc licit(boolean explicit, Doc doc) {
    return wrap(explicit ? "(" : "{", explicit ? ")" : "}", doc);
  }

  static @NotNull Doc spaced(Doc symbol) {
    return Doc.cat(Doc.ONE_WS, symbol, Doc.ONE_WS);
  }

  static @NotNull Doc wrap(@NotNull Doc left, @NotNull Doc right, @NotNull Doc doc) {
    return cat(left, doc, right);
  }

  static @NotNull Doc wrap(String leftSymbol, String rightSymbol, Doc doc) {
    return wrap(symbol(leftSymbol), symbol(rightSymbol), doc);
  }

  static @NotNull Doc spacedWrap(@NotNull Doc left, @NotNull Doc right, @NotNull Doc doc) {
    return wrap(left, right, wrap(ONE_WS, ONE_WS, doc));
  }

  static @NotNull Doc spacedWrap(@NotNull String leftSymbol, @NotNull String rightSymbol, @NotNull Doc doc) {
    return spacedWrap(symbol(leftSymbol), symbol(rightSymbol), doc);
  }

  /** @param falsification when false, add braces */
  static @NotNull Doc bracedUnless(Doc doc, boolean falsification) {
    return falsification ? doc : braced(doc);
  }

  static @NotNull Doc braced(Doc doc) {
    return wrap("{", "}", doc);
  }

  /**
   * Either `{ defaultDoc }` or `{\nflatDoc\n}`
   */
  static @NotNull Doc flatAltBracedBlock(Doc defaultDoc, Doc flatDoc) {
    return flatAlt(
      stickySep(Doc.symbol("{"), defaultDoc, Doc.symbol("}")),
      vcat(Doc.symbol("{"), flatDoc, Doc.symbol("}"))
    );
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
  static @NotNull Doc emptyIf(boolean cond, Supplier<@NotNull Doc> otherwise) {
    return cond ? empty() : otherwise.get();
  }

  /**
   * The empty document; conceptually the unit of 'Cat'
   *
   * @return empty document
   */
  static @NotNull Doc empty() {
    return Empty.INSTANCE;
  }

  /**
   * By default, flatAlt renders as {@param defaultDoc}. However, when 'group'-ed,
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
   * @param docBuilder document generator when current position provided
   * @return column action document
   */
  @Contract("_ -> new")
  static @NotNull Doc column(@NotNull IntFunction<Doc> docBuilder) {
    return new Column(docBuilder);
  }

  /**
   * Layout a document depending on the current 'nest'-ing level.
   * {@link Doc#align(Doc)} is implemented in terms of {@code nesting}.
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
   * <p>
   * For differences between {@link Doc#hang(int, Doc)}, {@link Doc#indent(int, Doc)}
   * and {@link Doc#nest(int, Doc)}, see unit tests in file "DocStringPrinterTest.java".
   *
   * @param indent indentation of the following lines
   * @param doc    the document to lay out
   * @return indented document
   */
  @Contract("_, _ -> new")
  static @NotNull Doc nest(int indent, @NotNull Doc doc) {
    return indent == 0 || doc.isEmpty() ? doc : new Nest(indent, doc);
  }

  /**
   * align lays out the document {@param doc} with the nesting level set to the
   * current column. It is used for example to implement {@link Doc#hang(int, Doc)}.
   * <p>
   * As an example, we will put a document right above another one, regardless of
   * the current nesting level. Without 'align'-ment, the second line is put simply
   * below everything we've had so far,
   * <p>
   * If we add an 'align' to the mix, the @'vsep'@'s contents all start in the
   * same column,
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
   * <p>
   * This differs from {@link Doc#nest(int, Doc)}, which is based on
   * the /current nesting level/ plus {@code indent}.
   * When you're not sure, try the more efficient 'nest' first. In our
   * example, this would yield
   * <p>
   * For differences between {@link Doc#hang(int, Doc)}, {@link Doc#indent(int, Doc)}
   * and {@link Doc#nest(int, Doc)}, see unit tests in file "DocStringPrinterTest.java".
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
   * This method indent document {@param doc} by {@param indent} columns,
   * * starting from the current cursor position.
   * <p>
   * This differs from {@link Doc#hang(int, Doc)}, which starts from the
   * next line.
   * <p>
   * For differences between {@link Doc#hang(int, Doc)}, {@link Doc#indent(int, Doc)}
   * and {@link Doc#nest(int, Doc)}, see unit tests in file "DocStringPrinterTest.java".
   *
   * @param indent Number of spaces to increase indentation by
   * @return The indented document
   */
  @Contract("_, _ -> new")
  static @NotNull Doc indent(int indent, @NotNull Doc doc) {
    return hang(indent, cat(spaces(indent), doc));
  }

  static @NotNull Doc spaces(int n) {
    return n <= 0 ? empty() : Doc.plain(" ".repeat(n));
  }

  /**
   * Paragraph indentation: indent {@param doc} by {@param indent} columns,
   * and then indent the first line again by {@param indent} columns.
   * This should be used at the line start.
   */
  @Contract("_, _ -> new")
  static @NotNull Doc par(int indent, @NotNull Doc doc) {
    return nest(indent, cat(spaces(indent), doc));
  }

  /**
   * Concat {@param left}, {@param delim} and {@param right}, with
   * {@param left} occupying at least {@param minBeforeDelim} length.
   * The "R" in method name stands for "right", which means the delim is placed near the right.
   * <p>
   * This behaves like {@code printf("%-*s%s%s", minBeforeDelim, left, delim, right);}
   * For example:
   * <pre>
   *   var doc = split(8, plain("Help"), plain(":"), english("Show the help message"));
   *   assertEquals("Help    :Show the help message", doc.commonRender());
   * </pre>
   *
   * @param minBeforeDelim The minimum length before {@param delim}
   * @apiNote {@param left}, {@param delim}, {@param right} should all be 1-line documents
   */
  static @NotNull Doc splitR(int minBeforeDelim, @NotNull Doc left, @NotNull Doc delim, @NotNull Doc right) {
    var alignedMiddle = column(k -> nesting(i -> indent(minBeforeDelim - k + i, delim)));
    return Doc.cat(left, alignedMiddle, Doc.align(right));
  }

  /**
   * Concat {@param left}, {@param delim} and {@param right}, with
   * {@param left} and {@param delim} occupying at least {@param minBeforeRight} length.
   * The "L" in method name stands for "left", which means the delim is placed near the left.
   * <p>
   * This behaves like {@code printf("%*s%s", minBeforeRight, (left ++ delim), right);}
   * For example:
   * <pre>
   *   var doc = splitR(8, plain("Help"), plain(":"), english("Show the help message"));
   *   assertEquals("Help:   Show the help message", doc.commonRender());
   * </pre>
   *
   * @param minBeforeRight The minimum length before {@param right}
   * @apiNote {@param left}, {@param delim}, {@param right} should all be 1-line documents
   */
  static @NotNull Doc splitL(int minBeforeRight, @NotNull Doc left, @NotNull Doc delim, @NotNull Doc right) {
    var alignedRight = column(k -> nesting(i -> indent(minBeforeRight - k + i, Doc.align(right))));
    return Doc.cat(left, delim, alignedRight);
  }

  static @NotNull Doc catBlockR(int minBeforeDelim, @NotNull SeqLike<Doc> left, @NotNull Doc delim, @NotNull SeqLike<Doc> right) {
    var vs = left.zipView(right).map(p -> Doc.splitR(minBeforeDelim, p.component1(), delim, p.component2()));
    return Doc.vcat(vs);
  }

  static @NotNull Doc catBlockL(int minBeforeRight, @NotNull SeqLike<Doc> left, @NotNull Doc delim, @NotNull SeqLike<Doc> right) {
    var vs = left.zipView(right).map(p -> Doc.splitL(minBeforeRight, p.component1(), delim, p.component2()));
    return Doc.vcat(vs);
  }

  /**
   * Creates a C-style indented block of statements.
   * <pre>
   *   prefix {
   *   [indent]block
   *   }
   * </pre>
   */
  @Contract("_, _, _ -> new")
  static @NotNull Doc cblock(@NotNull Doc prefix, int indent, @NotNull Doc block) {
    if (block.isEmpty()) return prefix;
    return Doc.vcat(Doc.sepNonEmpty(prefix, Doc.symbol("{")), Doc.nest(indent, Doc.vcat(block)), Doc.symbol("}"));
  }

  @Contract("_ -> new")
  static @NotNull Doc ordinal(int n) {
    var m = n % 100;
    if (m >= 4 && m <= 20) return plain(n + "th");
    return plain(n + switch (n % 10) {
      case 1 -> "st";
      case 2 -> "nd";
      case 3 -> "rd";
      default -> "th";
    });
  }

  /**
   * Plain text document. Backend will escape the text if it
   * contains offending characters.
   *
   * @param text text that may not contain '\n'
   * @return text document of the whole text
   */
  @Contract("_ -> new") static @NotNull Doc plain(String text) {
    if (text.isEmpty()) return empty();
    return new PlainText(text);
  }

  /**
   * Already escaped text that will be rendered as-is.
   * Callers should be responsible for escaping offending characters (like '\n', '<', etc.)
   * depending on the backend. Use with care as this may result in invalid output format.
   * <p>
   * Note that this is not the same as {@link Doc#code} or {@link Doc#codeBlock}.
   * Although in most cases code segments are treated as "already escaped" text
   * that will be rendered as-is. But for HTML, code segments is still escaped because
   * they are placed in `<code>` and `<pre>`.
   *
   * @param text text that will be rendered as-is.
   */
  @Contract("_ -> new") static @NotNull Doc escaped(String text) {
    return new EscapedText(text);
  }

  @Contract("_ -> new") static @NotNull Doc english(String text) {
    if (!text.contains(" ")) return plain(text);
    return sep(Seq.from(text.split(" ", -1))
      .view()
      .map(Doc::plain)
      .map(p -> flatAlt(p, cat(line(), p)))
    );
  }

  /**
   * @param symbol '\n' not allowed!
   * @return special symbol
   */
  @Contract("_ -> new") static @NotNull Doc symbol(String symbol) {
    assert !symbol.contains("\n");
    return new SpecialSymbol(symbol);
  }

  @Contract("_ -> new") static @NotNull Doc symbols(String... symbols) {
    return sep(Seq.of(symbols).map(Doc::symbol));
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
  @Contract("_ -> new") static @NotNull Doc cat(@NotNull SeqLike<Doc> docs) {
    return simpleCat(docs);
  }

  /** @see Doc#cat(Doc...) */
  @Contract("_ -> new") static @NotNull Doc cat(Doc @NotNull ... docs) {
    return cat(Seq.of(docs));
  }

  @Contract("_ -> new") static @NotNull Doc vcat(Doc @NotNull ... docs) {
    return join(line(), docs);
  }

  @Contract("_ -> new") static @NotNull Doc vcat(@NotNull SeqLike<@NotNull Doc> docs) {
    return join(line(), docs);
  }

  @Contract("_ -> new") static @NotNull Doc vcatNonEmpty(Doc @NotNull ... docs) {
    return vcatNonEmpty(Seq.of(docs));
  }

  @Contract("_ -> new") static @NotNull Doc vcatNonEmpty(@NotNull SeqLike<Doc> docs) {
    return vcat(docs.view().filter(Doc::isNotEmpty));
  }

  @Contract("_, _ -> new") static @NotNull Doc list(boolean isOrdered, @NotNull SeqLike<@NotNull Doc> docs) {
    return new List(isOrdered, docs.toImmutableSeq());
  }

  @Contract("_ -> new") static @NotNull Doc ordered(Doc @NotNull ... docs) {
    return list(true, Seq.of(docs));
  }

  @Contract("_ -> new") static @NotNull Doc bullet(Doc @NotNull ... docs) {
    return list(false, Seq.of(docs));
  }

  /**
   * stickySep concatenates all documents {@param docs} horizontally with a space,
   * i.e. it puts a space between all entries.
   * <p>
   * stickySep does not introduce line breaks on its own, even when the page is too narrow:
   *
   * @param docs documents to separate
   * @return separated documents
   */
  @Contract("_ -> new") static @NotNull Doc stickySep(@NotNull SeqLike<@NotNull Doc> docs) {
    return join(ONE_WS, docs);
  }

  @Contract("_ -> new") static @NotNull Doc stickySep(Doc @NotNull ... docs) {
    return join(ONE_WS, docs);
  }

  /**
   * fillSep concatenates the documents {@param docs} horizontally with a space
   * as long as it fits the page, then inserts a 'line' and continues doing that
   * for all documents in {@param docs}.
   * 'line' means that if 'group'-ed, the documents
   * are separated with a 'space' instead of newlines. Use {@link Doc#cat}
   * if you do not want a 'space'.
   * <p>
   * Let's print some words to fill the line:
   *
   * @param docs documents to separate
   * @return separated documents
   */
  @Contract("_ -> new") static @NotNull Doc sep(Doc @NotNull ... docs) {
    return join(ALT_WS, docs);
  }

  @Contract("_ -> new") static @NotNull Doc sep(@NotNull SeqLike<Doc> docs) {
    return join(ALT_WS, docs);
  }

  @Contract("_ -> new") static @NotNull Doc sepNonEmpty(Doc @NotNull ... docs) {
    return sepNonEmpty(Seq.of(docs));
  }

  @Contract("_ -> new") static @NotNull Doc sepNonEmpty(@NotNull SeqLike<Doc> docs) {
    return sep(docs.view().filter(Doc::isNotEmpty));
  }

  @Contract("_ -> new") static @NotNull Doc commaList(@NotNull SeqLike<Doc> docs) {
    return join(COMMA, docs);
  }

  @Contract("_ -> new") static @NotNull Doc vcommaList(@NotNull SeqLike<Doc> docs) {
    return join(cat(plain(","), line()), docs);
  }

  @Contract("_, _ -> new") static @NotNull Doc join(@NotNull Doc delim, Doc @NotNull ... docs) {
    return join(delim, Seq.of(docs));
  }

  @Contract("_, _ -> new")
  static @NotNull Doc join(@NotNull Doc delim, @NotNull SeqLike<@NotNull Doc> docs) {
    // See https://github.com/ice1000/aya-prover/issues/753
    var cache = docs.toImmutableSeq();
    if (cache.isEmpty()) return empty();
    var first = cache.getFirst();
    if (cache.sizeEquals(1)) return first;
    return simpleCat(cache.view().drop(1).foldLeft(MutableList.of(first), (l, r) -> {
      l.append(delim);
      l.append(r);
      return l;
    }));
  }

  /**
   * Unconditionally line break
   *
   * @return hard line document
   */
  @Contract("-> new")
  static @NotNull Doc line() {
    return Line.INSTANCE;
  }

  //endregion
  private static @NotNull Doc simpleCat(@NotNull SeqLike<@NotNull Doc> xs) {
    var seq = xs.toImmutableSeq();
    if (seq.isEmpty()) return empty();
    if (seq.sizeEquals(1)) return seq.getFirst();
    return new Cat(seq);
  }
}
