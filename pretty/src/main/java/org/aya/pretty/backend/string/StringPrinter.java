// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.string;

import kala.collection.Map;
import kala.tuple.Tuple;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.printer.Printer;
import org.aya.pretty.printer.PrinterConfig;
import org.jetbrains.annotations.NotNull;

/**
 * The class for all string-output printers.
 *
 * @author kiva
 */
public class StringPrinter<Config extends StringPrinterConfig<?>> implements Printer<String, Config> {
  /** renderer: where am I? */
  public enum Outer {
    Free,
    Code,
    EnclosingTag,
  }

  protected Config config;

  public @NotNull String makeIndent(int indent) {
    return " ".repeat(indent);
  }

  @Override
  public @NotNull String render(@NotNull Config config, @NotNull Doc doc) {
    this.config = config;
    var cursor = new Cursor(this);
    renderHeader(cursor);
    renderDoc(cursor, doc, Outer.Free);
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
      case Doc.PlainText text -> text.text().length();
      case Doc.SpecialSymbol symbol -> symbol.text().length();
      case Doc.HyperLinked text -> predictWidth(cursor, text.doc());
      case Doc.Styled styled -> predictWidth(cursor, styled.doc());
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

  protected void renderDoc(@NotNull Cursor cursor, @NotNull Doc doc, Outer outer) {
    switch (doc) {
      case Doc.PlainText text -> renderPlainText(cursor, text.text(), outer);
      case Doc.SpecialSymbol symbol -> renderSpecialSymbol(cursor, symbol.text(), outer);
      case Doc.HyperLinked text -> renderHyperLinked(cursor, text, outer);
      case Doc.Styled styled -> renderStyled(cursor, styled, outer);
      case Doc.Line d -> renderHardLineBreak(cursor);
      case Doc.FlatAlt alt -> renderFlatAlt(cursor, alt, outer);
      case Doc.Cat cat -> cat.inner().forEach(inner -> renderDoc(cursor, inner, outer));
      case Doc.Nest nest -> renderNest(cursor, nest, outer);
      case Doc.Union union -> renderUnionDoc(cursor, union, outer);
      case Doc.Column column -> renderDoc(cursor, column.docBuilder().apply(cursor.getCursor()), outer);
      case Doc.Nesting nesting -> renderDoc(cursor, nesting.docBuilder().apply(cursor.getNestLevel()), outer);
      case Doc.PageWidth pageWidth -> renderDoc(cursor, pageWidth.docBuilder().apply(config.getPageWidth()), outer);
      case Doc.CodeBlock codeBlock -> renderCodeBlock(cursor, codeBlock, Outer.Code);
      case Doc.InlineCode inlineCode -> renderInlineCode(cursor, inlineCode, Outer.Code);
      case Doc.Empty $ -> {}
    }
  }

  private static final @NotNull Map<String, String> unicodeMapping = Map.ofEntries(
    Tuple.of("Pi", "\u03A0"),
    Tuple.of("Sig", "\u03A3"),
    Tuple.of("Sigma", "\u03A3"),
    Tuple.of("\\", "\u03BB"),
    Tuple.of("/\\", "\u2227"),
    Tuple.of("\\/", "\u2228"),
    Tuple.of("=>", "\u21D2"),
    Tuple.of("ulift", "\u2191"),
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

  protected void renderSpecialSymbol(@NotNull Cursor cursor, @NotNull String text, Outer outer) {
    if (config.unicode) for (var k : unicodeMapping.keysView()) {
      if (text.trim().equals(k)) {
        cursor.visibleContent(text.replace(k, unicodeMapping.get(k)));
        return;
      }
    }
    renderPlainText(cursor, text, outer);
  }

  protected void renderNest(@NotNull Cursor cursor, @NotNull Doc.Nest nest, Outer outer) {
    cursor.nested(nest.indent(), () -> renderDoc(cursor, nest.doc(), outer));
  }

  protected void renderUnionDoc(@NotNull Cursor cursor, @NotNull Doc.Union union, Outer outer) {
    renderDoc(cursor, fitsBetter(cursor, union.shorterOne(), union.longerOne()), outer);
  }

  protected void renderFlatAlt(@NotNull Cursor cursor, @NotNull Doc.FlatAlt alt, Outer outer) {
    renderDoc(cursor, fitsBetter(cursor, alt.defaultDoc(), alt.preferWhenFlatten()), outer);
  }

  protected void renderHyperLinked(@NotNull Cursor cursor, @NotNull Doc.HyperLinked text, Outer outer) {
    renderDoc(cursor, text.doc(), outer);
  }

  protected void renderStyled(@NotNull Cursor cursor, @NotNull Doc.Styled styled, Outer outer) {
    var stylist = prepareStylist();
    stylist.format(styled.styles(), cursor, outer, () -> renderDoc(cursor, styled.doc(), outer));
  }

  protected @NotNull StringStylist prepareStylist() {
    return config.getStylist();
  }

  protected void renderPlainText(@NotNull Cursor cursor, @NotNull String content, Outer outer) {
    cursor.visibleContent(escapePlainText(content, outer));
  }

  protected @NotNull String escapePlainText(@NotNull String content, Outer outer) {
    return content;
  }

  protected void renderHardLineBreak(@NotNull Cursor cursor) {
    cursor.lineBreakWith("\n");
  }

  protected void renderCodeBlock(@NotNull Cursor cursor, @NotNull Doc.CodeBlock block, Outer outer) {
    renderDoc(cursor, block.code(), outer);
  }

  protected void renderInlineCode(@NotNull Cursor cursor, @NotNull Doc.InlineCode code, Outer outer) {
    cursor.visibleContent("`");
    renderDoc(cursor, code.code(), outer);
    cursor.visibleContent("`");
  }
}
