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
    // TODO: render doc to string
    throw new IllegalStateException("unimplemented");
  }
}
