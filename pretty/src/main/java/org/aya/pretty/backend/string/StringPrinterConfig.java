// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.string;

import org.aya.pretty.printer.PrinterConfig;
import org.jetbrains.annotations.NotNull;

public class StringPrinterConfig<S extends StringStylist> extends PrinterConfig.Basic<S> {
  public enum TextOptions implements PrinterConfig.Options<Boolean> {
    Unicode,
  }

  public enum StyleOptions implements PrinterConfig.Options<Boolean> {
    ServerSideRendering,
    AyaFlavored,
    SeparateStyle,
    HeaderCode,
    StyleCode,
  }

  public StringPrinterConfig(@NotNull S stylist, int pageWidth, boolean unicode) {
    this(stylist, pageWidth, unicode, false, false);
  }

  public StringPrinterConfig(@NotNull S stylist, int pageWidth, boolean unicode, boolean headerCode, boolean styleCode) {
    super(pageWidth, INFINITE_SIZE, stylist);
    set(TextOptions.Unicode, unicode);
    set(StyleOptions.HeaderCode, headerCode);
    set(StyleOptions.StyleCode, styleCode);
    set(StyleOptions.SeparateStyle, styleCode);
  }
}
