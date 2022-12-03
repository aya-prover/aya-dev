// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.md;

import org.aya.pretty.backend.html.DocHtmlPrinter;
import org.aya.pretty.backend.string.Cursor;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

public class DocMdPrinter extends DocHtmlPrinter<DocMdPrinter.Config> {
  @Override protected void renderHeader(@NotNull Cursor cursor) {
  }

  @Override protected void renderFooter(@NotNull Cursor cursor) {
  }

  @Override protected void renderHardLineBreak(@NotNull Cursor cursor) {
    cursor.lineBreakWith("\n");
  }

  @Override protected void renderHyperLinked(@NotNull Cursor cursor, @NotNull Doc.HyperLinked text) {
    if (config.getStylist() instanceof MdStylist) {
      // use markdown typesetting only when the stylist is pure markdown
      var href = text.href();
      cursor.invisibleContent("[");
      renderDoc(cursor, text.doc());
      cursor.invisibleContent("](");
      cursor.invisibleContent(href.id());
      cursor.invisibleContent(")");
      // TODO: text.id(), text.hover()
    } else super.renderHyperLinked(cursor, text);
  }

  public static class Config extends DocHtmlPrinter.Config {
    public Config() {
      this(MdStylist.DEFAULT);
    }

    public Config(@NotNull AyaMdStylist stylist) {
      super(stylist, false);
    }
  }
}
