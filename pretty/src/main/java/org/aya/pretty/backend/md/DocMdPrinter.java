// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.md;

import org.aya.pretty.backend.html.DocHtmlPrinter;
import org.aya.pretty.backend.html.Html5Stylist;
import org.aya.pretty.backend.string.Cursor;
import org.jetbrains.annotations.NotNull;

public class DocMdPrinter extends DocHtmlPrinter<DocMdPrinter.Config> {
  @Override protected void renderHeader(@NotNull Cursor cursor) {
  }

  @Override protected void renderFooter(@NotNull Cursor cursor) {
  }

  @Override protected void renderHardLineBreak(@NotNull Cursor cursor) {
    cursor.lineBreakWith("\n\n");
  }

  public static class Config extends DocHtmlPrinter.Config {
    public Config() {
      this(MdStylist.DEFAULT);
    }

    public Config(@NotNull Html5Stylist stylist) {
      super(stylist, false);
    }
  }
}
