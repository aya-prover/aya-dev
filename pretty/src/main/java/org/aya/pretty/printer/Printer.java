// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.printer;

import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

/**
 * This class was designed to support various PrettyPrint backend.
 * Example usage:
 * <pre>
 *   public class HtmlPrinter implements Printer[HtmlPrinterConfig] {}
 * </pre>
 * <p>
 * For a more practical example, see {@link org.aya.pretty.backend.string.StringPrinter}
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
