// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
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
public class StringPrinter<StringConfig extends StringPrinterConfig>
  implements Printer<String, StringConfig>, Cursor.CursorAPI {
  protected StringConfig config;

  @Override public @NotNull String makeIndent(int indent) {
    return " ".repeat(indent);
  }

  @Override
  public @NotNull String render(@NotNull StringConfig config, @NotNull Doc doc) {
    this.config = config;
    var cursor = new Cursor(this);
    renderHeader(cursor);
    renderDoc(cursor, doc);
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

  protected void renderDoc(@NotNull Cursor cursor, @NotNull Doc doc) {
    switch (doc) {
      case Doc.PlainText text -> renderPlainText(cursor, text.text());
      case Doc.SpecialSymbol symbol -> renderSpecialSymbol(cursor, symbol.text());
      case Doc.HyperLinked text -> renderHyperLinked(cursor, text);
      case Doc.Styled styled -> renderStyled(cursor, styled);
      case Doc.Line d -> renderHardLineBreak(cursor);
      case Doc.FlatAlt alt -> renderFlatAlt(cursor, alt);
      case Doc.Cat cat -> cat.inner().forEach(inner -> renderDoc(cursor, inner));
      case Doc.Nest nest -> renderNest(cursor, nest);
      case Doc.Union union -> renderUnionDoc(cursor, union);
      case Doc.Column column -> renderDoc(cursor, column.docBuilder().apply(cursor.getCursor()));
      case Doc.Nesting nesting -> renderDoc(cursor, nesting.docBuilder().apply(cursor.getNestLevel()));
      case Doc.PageWidth pageWidth -> renderDoc(cursor, pageWidth.docBuilder().apply(config.getPageWidth()));
      default -> {
      }
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
    Tuple.of("->", "\u2192")
  );

  protected void renderSpecialSymbol(@NotNull Cursor cursor, @NotNull String text) {
    if (config.unicode) for (var k : unicodeMapping.keysView()) {
      if (text.trim().equals(k)) {
        cursor.visibleContent(text.replace(k, unicodeMapping.get(k)));
        return;
      }
    }
    renderPlainText(cursor, text);
  }

  protected void renderNest(@NotNull Cursor cursor, @NotNull Doc.Nest nest) {
    cursor.nested(nest.indent(), () -> renderDoc(cursor, nest.doc()));
  }

  protected void renderUnionDoc(@NotNull Cursor cursor, @NotNull Doc.Union union) {
    renderDoc(cursor, fitsBetter(cursor, union.shorterOne(), union.longerOne()));
  }

  protected void renderFlatAlt(@NotNull Cursor cursor, @NotNull Doc.FlatAlt alt) {
    renderDoc(cursor, fitsBetter(cursor, alt.defaultDoc(), alt.preferWhenFlatten()));
  }

  protected void renderHyperLinked(@NotNull Cursor cursor, @NotNull Doc.HyperLinked text) {
    renderDoc(cursor, text.doc());
  }

  protected void renderStyled(@NotNull Cursor cursor, @NotNull Doc.Styled styled) {
    var stylist = config.getStylist();
    stylist.format(styled.styles(), cursor, () -> renderDoc(cursor, styled.doc()));
  }

  protected void renderPlainText(@NotNull Cursor cursor, @NotNull String content) {
    cursor.visibleContent(content);
  }

  protected void renderHardLineBreak(@NotNull Cursor cursor) {
    cursor.lineBreakWith("\n");
  }
}
