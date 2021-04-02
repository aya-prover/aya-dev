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
  implements Printer<String, StringConfig> {
  protected StringBuilder builder;
  protected StringConfig config;

  private int nestLevel = 0;
  protected int cursor = 0;

  @Override
  public @NotNull String render(@NotNull StringConfig config, @NotNull Doc doc) {
    builder = new StringBuilder();
    this.config = config;
    renderHeader();
    renderDoc(doc);
    renderFooter();
    return builder.toString();
  }

  private int lineRemaining() {
    var pw = config.getPageWidth();
    return pw == PrinterConfig.INFINITE_SIZE ? pw : pw - cursor;
  }

  private boolean isAtLineStart() {
    return cursor == 0;
  }

  protected int predictWidth(@NotNull Doc doc) {
    if (doc instanceof Doc.Fail) {
      throw new IllegalArgumentException("Doc.Fail passed to renderer");

    } else if (doc instanceof Doc.Empty) {
      return 0;

    } else if (doc instanceof Doc.PlainText text) {
      return text.text().length();

    } else if (doc instanceof Doc.HyperLinked text) {
      return predictWidth(text.doc());

    } else if (doc instanceof Doc.Styled styled) {
      return predictWidth(styled.doc());

    } else if (doc instanceof Doc.Line) {
      return 0;

    } else if (doc instanceof Doc.FlatAlt alt) {
      return predictWidth(alt.defaultDoc());

    } else if (doc instanceof Doc.Cat cat) {
      return predictWidth(cat.first()) + predictWidth(cat.second());

    } else if (doc instanceof Doc.Nest nest) {
      return predictWidth(nest.doc()) + nest.indent();

    } else if (doc instanceof Doc.Union union) {
      return predictWidth(union.longerOne());

    } else if (doc instanceof Doc.Column column) {
      return predictWidth(column.docBuilder().apply(cursor));

    } else if (doc instanceof Doc.Nesting nesting) {
      return predictWidth(nesting.docBuilder().apply(nestLevel));

    } else if (doc instanceof Doc.PageWidth pageWidth) {
      return predictWidth(pageWidth.docBuilder().apply(config.getPageWidth()));
    }

    throw new IllegalStateException("unreachable");
  }

  protected @NotNull Doc fitsBetter(@NotNull Doc a, @NotNull Doc b) {
    if (isAtLineStart()) {
      return a;
    }
    var lineRem = lineRemaining();
    return lineRem == PrinterConfig.INFINITE_SIZE || predictWidth(a) <= lineRem ? a : b;
  }

  protected void renderHeader() {
  }

  protected void renderFooter() {
  }

  protected void renderDoc(@NotNull Doc doc) {
    if (doc instanceof Doc.Fail) {
      throw new IllegalArgumentException("Doc.Fail passed to renderer");
    } else if (doc instanceof Doc.PlainText text) {
      renderPlainText(text.text());
      cursor += text.text().length();

    } else if (doc instanceof Doc.HyperLinked text) {
      renderHyperLinked(text);

    } else if (doc instanceof Doc.Styled styled) {
      renderStyled(styled);

    } else if (doc instanceof Doc.Line) {
      renderHardLineBreak();
      cursor = 0;

    } else if (doc instanceof Doc.FlatAlt alt) {
      renderFlatAlt(alt);

    } else if (doc instanceof Doc.Cat cat) {
      renderDoc(cat.first());
      renderDoc(cat.second());

    } else if (doc instanceof Doc.Nest nest) {
      renderNest(nest);

    } else if (doc instanceof Doc.Union union) {
      renderUnionDoc(union);

    } else if (doc instanceof Doc.Column column) {
      renderDoc(column.docBuilder().apply(cursor));

    } else if (doc instanceof Doc.Nesting nesting) {
      renderDoc(nesting.docBuilder().apply(nestLevel));

    } else if (doc instanceof Doc.PageWidth pageWidth) {
      renderDoc(pageWidth.docBuilder().apply(config.getPageWidth()));
    }
  }

  protected void renderNest(@NotNull Doc.Nest nest) {
    nestLevel += nest.indent();
    renderDoc(nest.doc());
    nestLevel -= nest.indent();
  }

  protected void renderUnionDoc(@NotNull Doc.Union union) {
    renderDoc(fitsBetter(union.shorterOne(), union.longerOne()));
  }

  protected void renderFlatAlt(@NotNull Doc.FlatAlt alt) {
    renderDoc(fitsBetter(alt.defaultDoc(), alt.preferWhenFlatten()));
  }

  protected void renderHyperLinked(@NotNull Doc.HyperLinked text) {
    renderDoc(text.doc());
  }

  protected void renderStyled(@NotNull Doc.Styled styled) {
    var formatter = config.getStyleFormatter();
    formatter.format(styled.styles(), builder, () -> renderDoc(styled.doc()));
  }

  protected void renderPlainText(@NotNull String content) {
    if (isAtLineStart()) {
      renderLineStart();
      renderIndent(nestLevel);
      cursor += nestLevel;
    }
    builder.append(content);
  }

  protected void renderLineStart() {
  }

  protected void renderIndent(int indent) {
    builder.append(" ".repeat(indent));
  }

  protected void renderHardLineBreak() {
    builder.append('\n');
  }
}
