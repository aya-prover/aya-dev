// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.backend;

import org.aya.pretty.backend.html.HtmlPrinterConfig;
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
    <!DOCTYPE html>
    <html lang="en"><head>
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

  @Override protected void renderHeader() {
    builder.append(HEAD);
  }

  @Override protected void renderFooter() {
    builder.append("""
      </pre>
      </body></html>
      """);
  }

  @Override protected void renderHyperLinked(Doc.@NotNull HyperLinked text) {
    if (text.link() instanceof StringLink link) {
      builder.append("<a ");
      if (text.id() != null) builder.append("id=\"").append(text.id()).append("\" ");
      builder.append("href=\"");
      builder.append(link.linkText());
      builder.append("\">");
      renderDoc(text.doc());
      builder.append("</a>");
    } else super.renderHyperLinked(text);
  }

  @Override protected void renderHardLineBreak() {
    builder.append("<br>");
  }
}
