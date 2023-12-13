// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.string;

import kala.collection.Map;
import kala.tuple.Tuple;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.printer.Printer;
import org.aya.pretty.printer.PrinterConfig;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.function.IntFunction;

import static org.aya.pretty.backend.string.StringPrinterConfig.TextOptions.Unicode;

/**
 * The class for all string-output printers.
 *
 * @author kiva
 */
public class StringPrinter<Config extends StringPrinterConfig<?>> implements Printer<String, Config> {
  /** renderer: where am I? */
  public enum Outer {
    Code,
    Math,
    EnclosingTag,
    List,
  }

  public static final @NotNull EnumSet<Outer> FREE = EnumSet.noneOf(Outer.class);

  protected Config config;

  protected @NotNull String makeIndent(int indent) {
    return " ".repeat(indent);
  }

  @Override public @NotNull String render(@NotNull Config config, @NotNull Doc doc) {
    this.config = config;
    var cursor = new Cursor(this);
    renderHeader(cursor);
    renderDoc(cursor, doc, FREE);
    renderFooter(cursor);
    return cursor.result().toString();
  }

  private int lineRemaining(@NotNull Cursor cursor) {
    var pw = config.getPageWidth();
    return pw == PrinterConfig.INFINITE_SIZE ? pw : pw - cursor.getCursor();
  }

  protected int predictWidth(@NotNull Cursor cursor, @NotNull Doc doc) {
    return switch (doc) {
      case Doc.Empty d -> 0;
      case Doc.PlainText(var text) -> text.length();
      case Doc.EscapedText(var text) -> text.length();
      case Doc.SpecialSymbol(var text) -> text.length();
      case Doc.HyperLinked text -> predictWidth(cursor, text.doc());
      case Doc.Image i -> predictWidth(cursor, i.alt());
      case Doc.Styled styled -> predictWidth(cursor, styled.doc());
      case Doc.Tooltip tooltip -> predictWidth(cursor, tooltip.doc());
      case Doc.Line d -> 0;
      case Doc.FlatAlt alt -> predictWidth(cursor, alt.defaultDoc());
      case Doc.Cat cat -> cat.inner().view().map(inner -> predictWidth(cursor, inner)).reduce(Integer::sum);
      case Doc.Nest nest -> predictWidth(cursor, nest.doc()) + nest.indent();
      case Doc.Union union -> predictWidth(cursor, union.longerOne());
      case Doc.Column column -> predictWidth(cursor, column.docBuilder().apply(cursor.getCursor()));
      case Doc.Nesting nesting -> predictWidth(cursor, nesting.docBuilder().apply(cursor.getNestLevel()));
      case Doc.PageWidth pageWidth -> predictWidth(cursor, pageWidth.docBuilder().apply(config.getPageWidth()));
      case Doc.CodeBlock codeBlock -> predictWidth(cursor, codeBlock.code());
      case Doc.InlineCode inlineCode -> predictWidth(cursor, inlineCode.code());
      case Doc.InlineMath inlineMath -> predictWidth(cursor, inlineMath.formula());
      case Doc.MathBlock mathBlock -> predictWidth(cursor, mathBlock.formula());
      case Doc.List list -> list.items().view().map(x -> predictWidth(cursor, x)).reduce(Integer::sum);
    };
  }

  protected @NotNull Doc fitsBetter(@NotNull Cursor cursor, @NotNull Doc a, @NotNull Doc b) {
    if (cursor.isAtLineStart()) {
      return a;
    }
    var lineRem = lineRemaining(cursor);
    return lineRem == PrinterConfig.INFINITE_SIZE || predictWidth(cursor, a) <= lineRem ? a : b;
  }

  protected void renderHeader(@NotNull Cursor cursor) {
  }

  protected void renderFooter(@NotNull Cursor cursor) {
  }

  protected void renderDoc(@NotNull Cursor cursor, @NotNull Doc doc, EnumSet<Outer> outer) {
    switch (doc) {
      case Doc.PlainText(var text) -> renderPlainText(cursor, text, outer);
      case Doc.EscapedText(var text) -> cursor.visibleContent(text);
      case Doc.SpecialSymbol(var symbol) -> renderSpecialSymbol(cursor, symbol, outer);
      case Doc.HyperLinked text -> renderHyperLinked(cursor, text, outer);
      case Doc.Image image -> renderImage(cursor, image, outer);
      case Doc.Styled styled -> renderStyled(cursor, styled, outer);
      case Doc.Line d -> renderHardLineBreak(cursor, outer);
      case Doc.FlatAlt alt -> renderFlatAlt(cursor, alt, outer);
      case Doc.Cat cat -> cat.inner().forEach(inner -> renderDoc(cursor, inner, outer));
      case Doc.Nest nest -> renderNest(cursor, nest, outer);
      case Doc.Union union -> renderUnionDoc(cursor, union, outer);
      case Doc.Column column -> renderDoc(cursor, column.docBuilder().apply(cursor.getCursor()), outer);
      case Doc.Nesting nesting -> renderDoc(cursor, nesting.docBuilder().apply(cursor.getNestLevel()), outer);
      case Doc.PageWidth pageWidth -> renderDoc(cursor, pageWidth.docBuilder().apply(config.getPageWidth()), outer);
      case Doc.CodeBlock codeBlock -> renderCodeBlock(cursor, codeBlock, outer);
      case Doc.InlineCode inlineCode -> renderInlineCode(cursor, inlineCode, outer);
      case Doc.List list -> renderList(cursor, list, outer);
      case Doc.InlineMath inlineMath -> renderInlineMath(cursor, inlineMath, outer);
      case Doc.MathBlock mathBlock -> renderMathBlock(cursor, mathBlock, outer);
      case Doc.Tooltip tooltip -> renderTooltip(cursor, tooltip, outer);
      case Doc.Empty _ -> {}
    }
  }

  private static final @NotNull Map<String, String> unicodeMapping = Map.ofEntries(
    Tuple.of("Sig", "\u03A3"),
    Tuple.of("/\\", "\u2227"),
    Tuple.of("\\/", "\u2228"),
    Tuple.of("=>", "\u21D2"),
    Tuple.of("ulift", "\u2191"),
    Tuple.of("forall", "\u2200"),
    Tuple.of("->", "\u2192"),
    Tuple.of("_|_", "\u22A5"),
    Tuple.of("top", "\u22A4"),
    Tuple.of("(|", "\u2987"),
    Tuple.of("|)", "\u2988"),
    Tuple.of("{|", "\u2983"),
    Tuple.of("|}", "\u2984"),
    Tuple.of("[|", "\u27E6"),
    Tuple.of("|]", "\u27E7")
  );

  protected void renderSpecialSymbol(@NotNull Cursor cursor, @NotNull String text, EnumSet<Outer> outer) {
    if (config.opt(Unicode, false)) for (var k : unicodeMapping.keysView()) {
      if (text.trim().equals(k)) {
        cursor.visibleContent(text.replace(k, unicodeMapping.get(k)));
        return;
      }
    }
    renderPlainText(cursor, text, outer);
  }

  protected void renderNest(@NotNull Cursor cursor, @NotNull Doc.Nest nest, EnumSet<Outer> outer) {
    cursor.nested(nest.indent(), () -> renderDoc(cursor, nest.doc(), outer));
  }

  protected void renderUnionDoc(@NotNull Cursor cursor, @NotNull Doc.Union union, EnumSet<Outer> outer) {
    renderDoc(cursor, fitsBetter(cursor, union.shorterOne(), union.longerOne()), outer);
  }

  protected void renderFlatAlt(@NotNull Cursor cursor, @NotNull Doc.FlatAlt alt, EnumSet<Outer> outer) {
    renderDoc(cursor, fitsBetter(cursor, alt.defaultDoc(), alt.preferWhenFlatten()), outer);
  }

  protected void renderHyperLinked(@NotNull Cursor cursor, @NotNull Doc.HyperLinked text, EnumSet<Outer> outer) {
    renderDoc(cursor, text.doc(), outer);
  }

  protected void renderImage(@NotNull Cursor cursor, @NotNull Doc.Image image, EnumSet<Outer> outer) {
    renderDoc(cursor, image.alt(), outer);
  }

  protected void renderStyled(@NotNull Cursor cursor, @NotNull Doc.Styled styled, EnumSet<Outer> outer) {
    var stylist = prepareStylist();
    stylist.format(styled.styles(), cursor, outer, () -> renderDoc(cursor, styled.doc(), outer));
  }

  protected @NotNull StringStylist prepareStylist() {
    return config.getStylist();
  }

  protected void renderPlainText(@NotNull Cursor cursor, @NotNull String content, EnumSet<Outer> outer) {
    cursor.visibleContent(escapePlainText(content, outer));
  }

  protected @NotNull String escapePlainText(@NotNull String content, EnumSet<Outer> outer) {
    return content;
  }

  /**
   * This line break makes target source code beautiful (like .tex or .md generated from Doc).
   * It is not printed in the resulting document (like .pdf generated from .tex or .md),
   * since it only separates different block elements (list, code block, etc.) in generated source files.
   * <p>
   * The default implementation is to print a single `\n` character and move to new line.
   *
   * @apiNote This is called by {@link #renderCodeBlock}, {@link #renderMathBlock}, {@link #formatList},
   * and other block rendering methods to separate the current block from the previous one.
   */
  protected void renderBlockSeparator(@NotNull Cursor cursor, EnumSet<Outer> outer) {
    cursor.lineBreakWith("\n");
  }

  /**
   * This line break is printed in the resulting document (like .pdf generated from .tex or .md).
   * The default implementation is same as {@link #renderBlockSeparator}.
   * Backends may override this method if the source code line break is different from
   * the printed line break, (like LaTeX use '\\' for new line in the printed document).
   */
  protected void renderHardLineBreak(@NotNull Cursor cursor, EnumSet<Outer> outer) {
    renderBlockSeparator(cursor, outer);
  }

  protected void renderTooltip(@NotNull Cursor cursor, @NotNull Doc.Tooltip tooltip, EnumSet<Outer> outer) {
    renderDoc(cursor, tooltip.doc(), outer);
  }

  protected void renderCodeBlock(@NotNull Cursor cursor, @NotNull Doc.CodeBlock block, EnumSet<Outer> outer) {
    separateBlockIfNeeded(cursor, outer);
    formatBlock(cursor, block.code(), "", "", EnumSet.of(Outer.Code));
  }

  protected void renderInlineCode(@NotNull Cursor cursor, @NotNull Doc.InlineCode code, EnumSet<Outer> outer) {
    // do not use `formatInline` here, because the backtick (`) should be visible in plain string,
    // while `formatInline` treats them as invisible.
    cursor.visibleContent("`");
    renderDoc(cursor, code.code(), EnumSet.of(Outer.Code));
    cursor.visibleContent("`");
  }

  protected void renderMathBlock(@NotNull Cursor cursor, @NotNull Doc.MathBlock block, EnumSet<Outer> outer) {
    separateBlockIfNeeded(cursor, outer);
    formatBlock(cursor, block.formula(), "", "", EnumSet.of(Outer.Math));
  }

  protected void renderInlineMath(@NotNull Cursor cursor, @NotNull Doc.InlineMath code, EnumSet<Outer> outer) {
    renderDoc(cursor, code.formula(), EnumSet.of(Outer.Math));
  }

  protected void renderList(@NotNull Cursor cursor, @NotNull Doc.List list, EnumSet<Outer> outer) {
    formatList(cursor, list, outer);
  }

  protected void formatList(@NotNull Cursor cursor, @NotNull Doc.List list, EnumSet<Outer> outer) {
    formatList(cursor, list, idx -> list.isOrdered() ? (idx + 1) + "." : "-", outer);
  }

  protected void formatList(@NotNull Cursor cursor, @NotNull Doc.List list, @NotNull IntFunction<String> itemBegin, EnumSet<Outer> outer) {
    // Move to new line if needed, in case the list begins at the end of the previous doc.
    separateBlockIfNeeded(cursor, outer);

    // The items should be placed one by one, each at the beginning of a line.
    var items = Doc.vcat(list.items().mapIndexed((idx, item) -> {
      // The beginning mark
      var pre = itemBegin.apply(idx);
      // The item content
      var content = Doc.align(item);
      return Doc.stickySep(Doc.escaped(pre), content);
    }));
    renderDoc(cursor, items, EnumSet.of(Outer.List));

    // Top level list should have a line after it, or the following content will be treated as list item.
    if (!outer.contains(Outer.List)) {
      separateBlockIfNeeded(cursor, outer);
      renderBlockSeparator(cursor, outer);
    }
  }

  /** renderBlockSeparator if not line start */
  protected void separateBlockIfNeeded(@NotNull Cursor cursor, EnumSet<Outer> outer) {
    cursor.whenLineUsed(() -> renderBlockSeparator(cursor, outer));
  }

  protected void formatBlock(@NotNull Cursor cursor, @NotNull Doc doc, @NotNull String begin, @NotNull String end, EnumSet<Outer> outer) {
    formatBlock(cursor, begin, end, outer, () -> renderDoc(cursor, doc, outer));
  }

  /**
   * Render the resulting document as:
   * <pre>
   * begin\n
   * inside()\n
   * end\n
   * </pre>
   */
  protected void formatBlock(@NotNull Cursor cursor, @NotNull String begin, @NotNull String end, EnumSet<Outer> outer, @NotNull Runnable inside) {
    cursor.invisibleContent(begin);
    renderBlockSeparator(cursor, outer);
    inside.run();
    renderBlockSeparator(cursor, outer);
    cursor.invisibleContent(end);
    renderBlockSeparator(cursor, outer);
  }

  /**
   * Render the resulting document as:
   * <pre>
   * begin·doc·end
   * </pre>
   */
  protected void formatInline(@NotNull Cursor cursor, @NotNull Doc doc, @NotNull String begin, @NotNull String end, EnumSet<Outer> outer) {
    cursor.invisibleContent(begin);
    renderDoc(cursor, doc, outer);
    cursor.invisibleContent(end);
  }
}
