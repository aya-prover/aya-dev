// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.terminal;

import org.aya.pretty.backend.string.Cursor;
import org.aya.pretty.backend.string.StringPrinter;
import org.aya.pretty.backend.string.StringPrinterConfig;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

public class DocTermPrinter extends StringPrinter<DocTermPrinter.Config> {
  @Override protected void renderInlineCode(@NotNull Cursor cursor, Doc.@NotNull InlineCode code, EnumSet<Outer> outer) {
    cursor.visibleContent("`");
    renderDoc(cursor, code.code(), EnumSet.of(Outer.Code));
    cursor.visibleContent("'");
  }

  public static class Config extends StringPrinterConfig<UnixTermStylist> {
    public Config(@NotNull UnixTermStylist stylist) {
      super(stylist);
    }
  }
}
