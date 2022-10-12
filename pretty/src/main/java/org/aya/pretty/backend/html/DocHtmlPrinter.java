// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.html;

import org.aya.pretty.backend.string.Cursor;
import org.aya.pretty.backend.string.StringPrinter;
import org.aya.pretty.backend.string.StringPrinterConfig;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.printer.ColorScheme;
import org.aya.pretty.printer.StyleFamily;
import org.aya.pretty.style.AyaColorScheme;
import org.aya.pretty.style.AyaStyleFamily;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

/**
 * Html backend, which ignores page width.
 */
public class DocHtmlPrinter extends StringPrinter<DocHtmlPrinter.Config> {
  @Language(value = "HTML")
  private static final @NotNull String HEAD = """
    <!DOCTYPE html><html lang="en"><head>
    <title>Aya file</title>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <script>
    var highlight = function (on) {
      return function () {
        var links = document.getElementsByTagName('a');
        for (var i = 0; i < links.length; i++) {
          var that = links[i];
          if (this.href != that.href) continue;
          if (on) that.classList.add("hover-highlight");
          else that.classList.remove("hover-highlight");
        }
      }
    };
    window.onload = function () {
      var links = document.getElementsByTagName('a');
      for (var i = 0; i < links.length; i++) {
        var link = links[i];
        if (!link.hasAttribute("href")) continue;
        link.onmouseover = highlight(true);
        link.onmouseout = highlight(false);
      }
    };
    </script>
    <style>
    .Aya a { text-decoration: none; color: black; }
    .Aya a[href]:hover { background-color: #B4EEB4; }
    .Aya [href].hover-highlight { background-color: #B4EEB4; }
    </style>
    </head><body>
    <pre class="Aya">
    """;

  @Override protected void renderHeader(@NotNull Cursor cursor) {
    if (config.withHeader) cursor.invisibleContent(HEAD);
    else cursor.invisibleContent("<pre class=\"Aya\">");
  }

  @Override protected void renderFooter(@NotNull Cursor cursor) {
    cursor.invisibleContent("</pre>");
    if (config.withHeader) cursor.invisibleContent("</body></html>");
  }

  @Override protected void renderHyperLinked(@NotNull Cursor cursor, Doc.@NotNull HyperLinked text) {
    var link = text.link();
    cursor.invisibleContent("<a ");
    if (text.id() != null) cursor.invisibleContent("id=\"" + text.id() + "\" ");
    cursor.invisibleContent("href=\"");
    cursor.invisibleContent(link.id());
    cursor.invisibleContent("\">");
    renderDoc(cursor, text.doc());
    cursor.invisibleContent("</a>");
  }

  @Override protected void renderHardLineBreak(@NotNull Cursor cursor) {
    cursor.lineBreakWith("<br>");
  }

  public static class Config extends StringPrinterConfig {
    public final boolean withHeader;

    public Config(boolean withHeader) {
      this(AyaColorScheme.EMACS, AyaStyleFamily.DEFAULT, withHeader);
    }

    public Config(ColorScheme colorScheme, StyleFamily styleFamily, boolean withHeader) {
      super(new Html5Stylist(colorScheme, styleFamily), INFINITE_SIZE, true);
      this.withHeader = withHeader;
    }
  }
}
