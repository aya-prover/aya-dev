// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.html;

import kala.collection.immutable.ImmutableMap;
import org.aya.pretty.backend.string.Cursor;
import org.aya.pretty.backend.string.StringPrinter;
import org.aya.pretty.backend.string.StringPrinterConfig;
import org.aya.pretty.doc.Doc;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * Html backend, which ignores page width.
 */
public class DocHtmlPrinter<Config extends DocHtmlPrinter.Config> extends StringPrinter<Config> {
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
          if (this.href !== that.href) continue;
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

  // https://developer.mozilla.org/en-US/docs/Glossary/Entity
  public static final @NotNull Pattern entityPattern = Pattern.compile("[&<>\"]");
  public static final @NotNull ImmutableMap<String, String> entityMapping = ImmutableMap.of(
    "&", "&amp;",
    "<", "&lt;",
    ">", "&gt;",
    "\"", "&quot;"
  );

  @Override protected void renderHeader(@NotNull Cursor cursor) {
    if (config.withHeader) cursor.invisibleContent(HEAD);
    else cursor.invisibleContent("<pre class=\"Aya\">");
  }

  @Override protected void renderFooter(@NotNull Cursor cursor) {
    cursor.invisibleContent("</pre>");
    if (config.withHeader) cursor.invisibleContent("</body></html>");
  }

  @Override protected void renderPlainText(@NotNull Cursor cursor, @NotNull String content) {
    content = entityPattern.matcher(content).replaceAll(result ->
      entityMapping.get(result.group()));   // fail if bug
    super.renderPlainText(cursor, content);
  }

  @Override protected void renderHyperLinked(@NotNull Cursor cursor, Doc.@NotNull HyperLinked text) {
    var href = text.href();
    cursor.invisibleContent("<a ");
    if (text.id() != null) cursor.invisibleContent("id=\"" + text.id() + "\" ");
    if (text.hover() != null) cursor.invisibleContent("title=\"" + text.hover() + "\" ");
    cursor.invisibleContent("href=\"");
    cursor.invisibleContent(href.id());
    cursor.invisibleContent("\">");
    renderDoc(cursor, text.doc());
    cursor.invisibleContent("</a>");
  }

  @Override protected void renderHardLineBreak(@NotNull Cursor cursor) {
    cursor.lineBreakWith("<br>");
  }

  @Override protected void renderInlineCode(@NotNull Cursor cursor, Doc.@NotNull InlineCode code) {
    cursor.invisibleContent("<code>");
    renderDoc(cursor, code.code());
    cursor.invisibleContent("</code>");
  }

  @Override protected void renderCodeBlock(@NotNull Cursor cursor, Doc.@NotNull CodeBlock block) {
    cursor.invisibleContent("<pre class=\"" + block.language() + "\">");
    renderDoc(cursor, block.code());
    cursor.invisibleContent("</pre>");
  }

  public static class Config extends StringPrinterConfig {
    public final boolean withHeader;

    public Config(boolean withHeader) {
      this(Html5Stylist.DEFAULT, withHeader);
    }

    public Config(@NotNull Html5Stylist stylist, boolean withHeader) {
      super(stylist, INFINITE_SIZE, true);
      this.withHeader = withHeader;
    }
  }
}
