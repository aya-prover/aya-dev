// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.pretty.printer;

import org.jetbrains.annotations.NotNull;
import org.mzi.pretty.doc.Doc;

/**
 * This class was designed to support various PrettyPrint backend.
 * Example usage:
 * <pre>
 *   public class HtmlPrinter implements Printer<HtmlPrinterConfig> {}
 * </pre>
 * <p>
 * For a more practical example, see {@link org.mzi.pretty.backend.DocStringPrinter}
 *
 * @author kiva
 */
public interface Printer<Out, Config extends PrinterConfig> {
  /**
   * Render a {@link Doc} object with a config.
   *
   * @param config printer config
   * @param doc    doc object
   * @return rendered content
   */
  @NotNull Out render(@NotNull Config config, @NotNull Doc doc);
}
