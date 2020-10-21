package org.mzi.pretty.backend;

import org.jetbrains.annotations.NotNull;
import org.mzi.pretty.doc.Doc;
import org.mzi.pretty.printer.Printer;
import org.mzi.pretty.printer.PrinterConfig;

/**
 * The default String backend for Doc.
 * This backend ignores hyper text.
 *
 * @author kiva
 */
public class DocStringPrinter implements Printer<String, DocStringPrinter.Config> {
  public static class Config extends PrinterConfig.Basic {
    public Config() {
      this(PrinterConfig.INFINITE_SIZE);
    }

    public Config(int pageWidth) {
      super(pageWidth, PrinterConfig.INFINITE_SIZE);
    }
  }

  @Override
  public @NotNull String render(@NotNull Config config, @NotNull Doc doc) {
    var renderer = new RendererImpl(config);
    return renderer.render(doc);
  }

  private static class RendererImpl {
    StringBuilder builder = new StringBuilder();
    Config config;

    int nestLevel = 0;
    int cursor = 0;

    public RendererImpl(Config config) {
      this.config = config;
    }

    @NotNull String render(@NotNull Doc doc) {
      renderDoc(doc);
      return builder.toString();
    }

    private int lineRemaining() {
      return config.getPageWidth() - cursor;
    }

    private int predictWidth(@NotNull Doc doc) {
      if (doc instanceof Doc.Fail) {
        throw new IllegalArgumentException("Doc.Fail passed to renderer");

      } else if (doc instanceof Doc.PlainText text) {
        return text.text().length();

      } else if (doc instanceof Doc.HyperText text) {
        return text.text().length();

      } else if (doc instanceof Doc.Line) {
        // hard line break has 1 character '\n'
        return 1;

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

    private @NotNull Doc fitsBetter(@NotNull Doc a, @NotNull Doc b) {
      return predictWidth(a) <= lineRemaining()
        ? a
        : b;
    }

    private void renderDoc(@NotNull Doc doc) {
      if (doc instanceof Doc.Fail) {
        throw new IllegalArgumentException("Doc.Fail passed to renderer");

      } else if (doc instanceof Doc.PlainText text) {
        renderPlainText(text.text());

      } else if (doc instanceof Doc.HyperText text) {
        // we ignore hyper text link because we are plain text renderer
        renderPlainText(text.text());

      } else if (doc instanceof Doc.Line) {
        renderHardLineBreak();

      } else if (doc instanceof Doc.FlatAlt alt) {
        renderDoc(fitsBetter(alt.defaultDoc(), alt.preferWhenFlatten()));

      } else if (doc instanceof Doc.Cat cat) {
        renderDoc(cat.first());
        renderDoc(cat.second());

      } else if (doc instanceof Doc.Nest nest) {
        nestLevel += nest.indent();
        renderDoc(nest.doc());

      } else if (doc instanceof Doc.Union union) {
        renderDoc(fitsBetter(union.longerOne(), union.shorterOne()));

      } else if (doc instanceof Doc.Column column) {
        renderDoc(column.docBuilder().apply(cursor));

      } else if (doc instanceof Doc.Nesting nesting) {
        renderDoc(nesting.docBuilder().apply(nestLevel));

      } else if (doc instanceof Doc.PageWidth pageWidth) {
        renderDoc(pageWidth.docBuilder().apply(config.getPageWidth()));
      }

      throw new IllegalStateException("unreachable");
    }

    private void renderHardLineBreak() {
      cursor = 0;
      builder.append('\n');
    }

    private void renderPlainText(String content) {
      if (cursor == 0) {
        renderIndent(nestLevel);
      }
      builder.append(content);
      cursor += content.length();
    }

    private void renderIndent(int indent) {
      builder.append(" ".repeat(indent));
      cursor += indent;
    }
  }
}
