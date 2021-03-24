// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.backend.string;

import org.aya.pretty.doc.Doc;
import org.aya.pretty.printer.Printer;
import org.jetbrains.annotations.NotNull;

/**
 * The class for all string-output printers.
 * This backend can be customized by extending the renderer.
 * @see StringRenderer
 * @author kiva
 */
public class StringPrinter<StringConfig extends StringPrinterConfig>
  implements Printer<String, StringConfig> {
  private final StringRenderer<StringConfig> renderer;

  protected StringPrinter(StringRenderer<StringConfig> renderer) {
    this.renderer = renderer;
  }

  @Override
  public @NotNull String render(@NotNull StringConfig config, @NotNull Doc doc) {
    return renderer.renderOne(config, doc);
  }
}
