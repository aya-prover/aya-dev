// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.backend.string;

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
    return cursor.builder.toString();
  }

  private int lineRemaining(@NotNull Cursor cursor) {
    var pw = config.getPageWidth();
    return pw == PrinterConfig.INFINITE_SIZE ? pw : pw - cursor.cursor;
  }

  protected int predictWidth(@NotNull Cursor cursor, @NotNull Doc doc) {
    if (doc instanceof Doc.Empty) {
      return 0;
    } else if (doc instanceof Doc.PlainText text) {
      return text.text().length();
    } else if (doc instanceof Doc.SpecialSymbol symbol) {
      return symbol.text().length();
    } else if (doc instanceof Doc.HyperLinked text) {
      return predictWidth(cursor, text.doc());
    } else if (doc instanceof Doc.Styled styled) {
      return predictWidth(cursor, styled.doc());
    } else if (doc instanceof Doc.Line) {
      return 0;
    } else if (doc instanceof Doc.FlatAlt alt) {
      return predictWidth(cursor, alt.defaultDoc());
    } else if (doc instanceof Doc.Cat cat) {
      return predictWidth(cursor, cat.first()) + predictWidth(cursor, cat.second());
    } else if (doc instanceof Doc.Nest nest) {
      return predictWidth(cursor, nest.doc()) + nest.indent();
    } else if (doc instanceof Doc.Union union) {
      return predictWidth(cursor, union.longerOne());
    } else if (doc instanceof Doc.Column column) {
      return predictWidth(cursor, column.docBuilder().apply(cursor.cursor));
    } else if (doc instanceof Doc.Nesting nesting) {
      return predictWidth(cursor, nesting.docBuilder().apply(cursor.nestLevel));
    } else if (doc instanceof Doc.PageWidth pageWidth) {
      return predictWidth(cursor, pageWidth.docBuilder().apply(config.getPageWidth()));
    }
    throw new IllegalStateException("unreachable");
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
    if (doc instanceof Doc.PlainText text) {
      renderPlainText(cursor, text.text());
    } else if (doc instanceof Doc.SpecialSymbol symbol) {
      renderSpecialSymbol(cursor, symbol.text());
    } else if (doc instanceof Doc.HyperLinked text) {
      renderHyperLinked(cursor, text);
    } else if (doc instanceof Doc.Styled styled) {
      renderStyled(cursor, styled);
    } else if (doc instanceof Doc.Line) {
      renderHardLineBreak(cursor);
      renderLineStart(cursor);
    } else if (doc instanceof Doc.FlatAlt alt) {
      renderFlatAlt(cursor, alt);
    } else if (doc instanceof Doc.Cat cat) {
      renderDoc(cursor, cat.first());
      renderDoc(cursor, cat.second());
    } else if (doc instanceof Doc.Nest nest) {
      renderNest(cursor, nest);
    } else if (doc instanceof Doc.Union union) {
      renderUnionDoc(cursor, union);
    } else if (doc instanceof Doc.Column column) {
      renderDoc(cursor, column.docBuilder().apply(cursor.cursor));
    } else if (doc instanceof Doc.Nesting nesting) {
      renderDoc(cursor, nesting.docBuilder().apply(cursor.nestLevel));
    } else if (doc instanceof Doc.PageWidth pageWidth) {
      renderDoc(cursor, pageWidth.docBuilder().apply(config.getPageWidth()));
    }
  }

  protected void renderSpecialSymbol(@NotNull Cursor cursor, @NotNull String text) {
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
    var formatter = config.getStyleFormatter();
    formatter.format(styled.styles(), cursor, () -> renderDoc(cursor, styled.doc()));
  }

  protected void renderPlainText(@NotNull Cursor cursor, @NotNull String content) {
    // if (cursor.isAtLineStart()) {
    //   renderIndent(cursor, nestLevel);
    // }
    cursor.visibleContent(content);
  }

  protected void renderLineStart(@NotNull Cursor cursor) {
  }

  protected void renderHardLineBreak(@NotNull Cursor cursor) {
    cursor.lineBreakWith("\n");
  }
}
