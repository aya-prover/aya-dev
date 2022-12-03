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

  @Override protected @NotNull String escapePlainText(@NotNull String content) {
    return content; // TODO: markdown escape: https://spec.commonmark.org/0.30/#backslash-escapes
  }

  @Override protected void renderHardLineBreak(@NotNull Cursor cursor) {
    cursor.lineBreakWith("\n");
  }

  @Override protected void renderHyperLinked(@NotNull Cursor cursor, @NotNull Doc.HyperLinked text, Outer outer) {
    runSwitch(() -> {
      // use markdown typesetting only when the stylist is pure markdown
      var href = text.href();
      cursor.invisibleContent("[");
      renderDoc(cursor, text.doc(), outer);
      cursor.invisibleContent("](");
      cursor.invisibleContent(href.id());
      cursor.invisibleContent(")");
      // TODO: text.id(), text.hover()
    }, () -> super.renderHyperLinked(cursor, text, outer));
  }

  @Override protected void renderInlineCode(@NotNull Cursor cursor, @NotNull Doc.InlineCode code, Outer outer) {
    runSwitch(() -> {
      cursor.invisibleContent("`");
      renderDoc(cursor, code.code(), outer);
      cursor.invisibleContent("`");
    }, () -> super.renderInlineCode(cursor, code, outer));
  }

  @Override protected void renderCodeBlock(@NotNull Cursor cursor, @NotNull Doc.CodeBlock block, Outer outer) {
    Runnable pureMd = () -> formatCodeBlock(cursor, block.code(), "```" + block.language(), "```", outer);
    runSwitch(
      pureMd,
      () -> {
        var isAya = block.language().equalsIgnoreCase("aya");
        if (isAya) formatCodeBlock(cursor, block.code(), "<pre class=\"Aya\">", "</pre>", outer);
        else pureMd.run();
      });
  }

  public void formatCodeBlock(@NotNull Cursor cursor, @NotNull Doc code, @NotNull String begin, @NotNull String end, Outer outer) {
    cursor.invisibleContent(begin);
    cursor.lineBreakWith("\n");
    renderDoc(cursor, code, outer);
    cursor.lineBreakWith("\n");
    cursor.invisibleContent(end);
    cursor.lineBreakWith("\n");
  }

  private void runSwitch(@NotNull Runnable pureMd, @NotNull Runnable ayaMd) {
    if (config.getStylist() instanceof MdStylist) pureMd.run();
    else ayaMd.run();
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
