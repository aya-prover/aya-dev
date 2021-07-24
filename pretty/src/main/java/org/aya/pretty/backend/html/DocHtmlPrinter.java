// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.backend.html;

import org.aya.pretty.backend.string.Cursor;
import org.aya.pretty.backend.string.StringLink;
import org.aya.pretty.backend.string.StringPrinter;
import org.aya.pretty.doc.Doc;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

/**
 * Html backend, which ignores page width.
 */
public class DocHtmlPrinter extends StringPrinter<HtmlPrinterConfig> {
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
    if (text.link() instanceof StringLink link) {
      cursor.invisibleContent("<a ");
      if (text.id() != null) cursor.invisibleContent("id=\"" + text.id() + "\" ");
      cursor.invisibleContent("href=\"");
      cursor.invisibleContent(link.linkText());
      cursor.invisibleContent("\">");
      renderDoc(cursor, text.doc());
      cursor.invisibleContent("</a>");
    } else super.renderHyperLinked(cursor, text);
  }

  @Override protected void renderHardLineBreak(@NotNull Cursor cursor) {
    cursor.lineBreakWith("<br>");
  }
}
