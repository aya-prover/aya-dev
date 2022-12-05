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
  public static final @NotNull String HOVER_POPUP_STYLE = """
    <style>
    .Aya .aya-hover {
      /* make absolute position available for hover popup */
      position: relative;
      cursor: pointer;
    }
    .Aya [aya-type]:after {
      /* hover text */
      content: attr(aya-type);
      visibility: hidden;
      /* above the text, aligned to left */
      position: absolute;
      top: 0;
      left: 0; /* 0% for left-aligned, 100% for right-aligned*/
      transform: translate(0px, -110%);
      /* spacing */
      white-space: pre;
      padding: 5px 10px;
      background-color: rgba(18,26,44,0.8);
      color: #fff;
      box-shadow: 1px 1px 14px rgba(0,0,0,0.1)
    }
    .Aya .aya-hover:hover:after {
      /* show on hover */
      transform: translate(0px, -110%);
      visibility: visible;
      display: block;
    }
    </style>
    """;
  @Language(value = "HTML")
  public static final @NotNull String HOVER_HIGHLIGHT_STYLE = """
    <style>
    .Aya a { text-decoration: none; color: black; }
    .Aya a[href]:hover { background-color: #B4EEB4; }
    .Aya [href].hover-highlight { background-color: #B4EEB4; }
    </style>
    """;
  @Language(value = "HTML")
  public static final @NotNull String HOVER_HIGHLIGHT_ALL_OCCURS = """
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
    """;

  @Language(value = "HTML")
  private static final @NotNull String HEAD = """
    <!DOCTYPE html><html lang="en"><head>
    <title>Aya file</title>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    """ + HOVER_HIGHLIGHT_ALL_OCCURS + HOVER_HIGHLIGHT_STYLE + HOVER_POPUP_STYLE + """
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

  @Override protected @NotNull String escapePlainText(@NotNull String content, Outer outer) {
    return entityPattern.matcher(content).replaceAll(
      result -> entityMapping.get(result.group()));   // fail if bug
  }

  @Override protected void renderHyperLinked(@NotNull Cursor cursor, Doc.@NotNull HyperLinked text, Outer outer) {
    var href = text.href();
    cursor.invisibleContent("<a ");
    if (text.id() != null) cursor.invisibleContent("id=\"" + text.id() + "\" ");
    if (text.hover() != null) {
      cursor.invisibleContent("class=\"aya-hover\" ");
      cursor.invisibleContent("aya-type=\"" + text.hover() + "\" ");
    }
    cursor.invisibleContent("href=\"");
    cursor.invisibleContent(href.id());
    cursor.invisibleContent("\">");
    renderDoc(cursor, text.doc(), Outer.EnclosingTag);
    cursor.invisibleContent("</a>");
  }

  @Override protected void renderHardLineBreak(@NotNull Cursor cursor) {
    cursor.lineBreakWith("<br>");
  }

  @Override protected void renderInlineCode(@NotNull Cursor cursor, Doc.@NotNull InlineCode code, Outer outer) {
    cursor.invisibleContent("<code>");
    renderDoc(cursor, code.code(), Outer.EnclosingTag); // Even in code mode, we still need to escape
    cursor.invisibleContent("</code>");
  }

  @Override protected void renderCodeBlock(@NotNull Cursor cursor, Doc.@NotNull CodeBlock block, Outer outer) {
    cursor.invisibleContent("<pre class=\"" + block.language() + "\">");
    renderDoc(cursor, block.code(), Outer.EnclosingTag); // Even in code mode, we still need to escape
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
