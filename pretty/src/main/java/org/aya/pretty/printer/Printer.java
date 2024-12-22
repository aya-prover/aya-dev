// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.printer;

import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

/// This class was designed to support various PrettyPrint backend.
/// Example usage:
/// ```java
///   public class HtmlPrinter implements Printer<HtmlPrinterConfig> {}
/// ```
///
/// For a more practical example, see [org.aya.pretty.backend.string.StringPrinter]
///
/// @author kiva
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
