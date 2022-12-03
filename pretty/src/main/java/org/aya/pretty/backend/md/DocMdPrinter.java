// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.md;

import org.aya.pretty.backend.html.DocHtmlPrinter;
import org.aya.pretty.backend.string.Cursor;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public class DocMdPrinter extends DocHtmlPrinter<DocMdPrinter.Config> {
  @Override protected void renderHeader(@NotNull Cursor cursor) {
  }

  @Override protected void renderFooter(@NotNull Cursor cursor) {
  }

  // markdown escape: https://spec.commonmark.org/0.30/#backslash-escapes
  @Override protected @NotNull String escapePlainText(@NotNull String content) {
    // We are not need to call `super.escapePlainText`, we will escape them in markdown way.
    // I wish you can understand this genius regexp
    return Pattern
      .compile("[!\"#$%&'()*+,-./:;<=>?@\\[\\\\\\]^_`{|}~]")
      .matcher(content)
      .replaceAll(result -> {
        var chara = result.group();
        // special characters, see Matcher#appendReplacement
        if (chara.equals("\\")) chara = "\\\\";
        if (chara.equals("$")) chara = "\\$";
        return "\\\\" + chara;
      });
  }

  @Override protected void renderHardLineBreak(@NotNull Cursor cursor) {
    cursor.lineBreakWith("\n");
  }

  @Override protected void renderHyperLinked(@NotNull Cursor cursor, @NotNull Doc.HyperLinked text, Outer outer) {
    Runnable pureMd = () -> {
      // use markdown typesetting only when the stylist is pure markdown
      var href = text.href();
      cursor.invisibleContent("[");
      renderDoc(cursor, text.doc(), outer);
      cursor.invisibleContent("](");
      cursor.invisibleContent(href.id());
      cursor.invisibleContent(")");
      // TODO: text.id(), text.hover()
    };
    runSwitch(pureMd, () -> {
      if (outer == Outer.Code) super.renderHyperLinked(cursor, text, outer);
      else pureMd.run();
    });
  }

  @Override protected void renderInlineCode(@NotNull Cursor cursor, @NotNull Doc.InlineCode code, Outer outer) {
    // assumption: inline code cannot be nested in markdown, but don't assert it.
    Runnable pureMd = () -> {
      cursor.invisibleContent("`");
      renderDoc(cursor, code.code(), outer);
      cursor.invisibleContent("`");
    };
    runSwitch(pureMd, () -> {
      var isAya = code.language().equalsIgnoreCase("aya");
      if (isAya) super.renderInlineCode(cursor, code, outer);
      else pureMd.run();
    });
  }

  @Override protected void renderCodeBlock(@NotNull Cursor cursor, @NotNull Doc.CodeBlock block, Outer outer) {
    // assumption: code block cannot be nested in markdown, but don't assert it.
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
    if (config.ayaFlavored) ayaMd.run();
    else pureMd.run();
  }

  public static class Config extends DocHtmlPrinter.Config {
    public boolean ayaFlavored;

    public Config(boolean ayaFlavored) {
      this(MdStylist.DEFAULT, ayaFlavored);
    }

    public Config(@NotNull MdStylist stylist, boolean ayaFlavored) {
      super(stylist, false);
      this.ayaFlavored = ayaFlavored;
    }
  }
}
