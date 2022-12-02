// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.md;

import org.aya.pretty.backend.string.Cursor;
import org.aya.pretty.backend.string.StringPrinter;
import org.aya.pretty.backend.string.StringPrinterConfig;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

public class DocMdPrinter extends StringPrinter<DocMdPrinter.Config> {
  @Override protected void renderHardLineBreak(@NotNull Cursor cursor) {
    cursor.lineBreakWith("\n\n");
  }

  @Override protected void renderHyperLinked(@NotNull Cursor cursor, Doc.@NotNull HyperLinked text) {
    var link = text.link();
    cursor.invisibleContent("[");
    renderDoc(cursor, text.doc());
    cursor.invisibleContent("](");
    cursor.invisibleContent(link.id());
    cursor.invisibleContent(")");
    // TODO: text.id()
  }

  public static class Config extends StringPrinterConfig {
    public Config() {
      this(MdStylist.DEFAULT);
    }

    public Config(@NotNull MdStylist stylist) {
      super(stylist, INFINITE_SIZE, false);
    }
  }
}
