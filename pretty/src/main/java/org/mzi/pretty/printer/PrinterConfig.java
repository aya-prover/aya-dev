package org.mzi.pretty.printer;

/**
 * This class was designed to support various PrettyPrint backend.
 * Example usage:
 * <pre>
 *   public class HtmlPrinterConfig implements PrinterConfig {}
 * </pre>
 * <p>
 * For a more practical example, see {@link org.mzi.pretty.backend.DocStringPrinter.Config}
 *
 * @author kiva
 */
public interface PrinterConfig {
  /**
   * Indicate that the width or height has infinite size.
   */
  int INFINITE_SIZE = -1;

  /**
   * The character count that a line can hold.
   *
   * @return page width or -1 for infinity page width.
   */
  default int getPageWidth() {
    return INFINITE_SIZE;
  }

  /**
   * The line count that a page can hold.
   *
   * @return page height or -1 for infinity page height.
   */
  default int getPageHeight() {
    return INFINITE_SIZE;
  }

  class Basic implements PrinterConfig {
    private final int pageWidth;
    private final int pageHeight;

    public Basic(int pageWidth, int pageHeight) {
      this.pageWidth = pageWidth;
      this.pageHeight = pageHeight;
    }

    @Override
    public int getPageWidth() {
      return pageWidth;
    }

    @Override
    public int getPageHeight() {
      return pageHeight;
    }
  }
}
