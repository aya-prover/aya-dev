// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.printer;

import kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.NotNull;

/**
 * This class was designed to support various PrettyPrint backend.
 * Example usage:
 * <pre>
 *   public class HtmlPrinterConfig implements PrinterConfig {}
 * </pre>
 * <p>
 * For a more practical example, see {@link org.aya.pretty.backend.string.StringPrinterConfig}
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

  @NotNull Stylist getStylist();

  interface Options<T> {
  }

  enum PageOptions implements Options<Integer> {
    PageWidth, PageHeight,
  }

  /**
   * Basic configure for other configs to easily extend config flags.
   */
  class Basic<S extends Stylist> implements PrinterConfig {
    protected final @NotNull MutableMap<Options<?>, Object> options = MutableMap.create();
    private final @NotNull S stylist;

    public Basic(@NotNull S stylist) {
      this.stylist = stylist;
    }

    @SuppressWarnings("unchecked") public @NotNull <T> T opt(@NotNull Options<T> key, @NotNull T defaultValue) {
      return (T) options.getOrDefault(key, defaultValue);
    }

    public <T> void set(@NotNull Options<T> key, @NotNull T value) {
      options.put(key, value);
    }

    @Override public @NotNull S getStylist() {
      return stylist;
    }

    @Override public int getPageWidth() {
      return opt(PageOptions.PageWidth, INFINITE_SIZE);
    }

    @Override public int getPageHeight() {
      return opt(PageOptions.PageHeight, INFINITE_SIZE);
    }
  }
}
