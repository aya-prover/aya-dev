// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.html;

import kala.collection.immutable.ImmutableMap;
import org.aya.pretty.backend.string.Cursor;
import org.aya.pretty.backend.string.LinkId;
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
  @Language(value = "JavaScript")
  private static final @NotNull String HOVER_HIGHLIGHT_ALL_OCCURS_JS_HIGHLIGHT_FN = """
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
    """;
  @Language(value = "JavaScript")
  private static final @NotNull String HOVER_HIGHLIGHT_ALL_OCCURS_JS_INIT = """
    var links = document.getElementsByTagName('a');
    for (var i = 0; i < links.length; i++) {
      var link = links[i];
      if (!link.hasAttribute("href")) continue;
      link.onmouseover = highlight(true);
      link.onmouseout = highlight(false);
    }
    """;
  @SuppressWarnings("LanguageMismatch")
  @Language(value = "HTML")
  public static final @NotNull String HOVER_HIGHLIGHT_ALL_OCCURS = """
    <script>
    """ + HOVER_HIGHLIGHT_ALL_OCCURS_JS_HIGHLIGHT_FN + """
    window.onload = function () {
    """ + HOVER_HIGHLIGHT_ALL_OCCURS_JS_INIT + """
    };
    </script>
    """;
  @SuppressWarnings("LanguageMismatch")
  @Language(value = "HTML")
  public static final @NotNull String HOVER_HIGHLIGHT_ALL_OCCURS_VUE = """
    <script>
    export default {
      mounted() {
    """ + HOVER_HIGHLIGHT_ALL_OCCURS_JS_HIGHLIGHT_FN + """
    """ + HOVER_HIGHLIGHT_ALL_OCCURS_JS_INIT + """
      }
    }
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

  /**
   * <a href="https://developer.mozilla.org/en-US/docs/Glossary/Entity">Mozilla doc: entity</a>
   * Added backslash for vitepress compatibility.
   */
  public static final @NotNull Pattern entityPattern = Pattern.compile("[&<>\"\\\\]");
  public static final @NotNull ImmutableMap<String, String> entityMapping = ImmutableMap.of(
    "&", "&amp;",
    "<", "&lt;",
    ">", "&gt;",
    "\\", "&bsol;",
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
    // note: HTML always needs escaping, regardless of `outer`
    return entityPattern.matcher(content).replaceAll(
      result -> entityMapping.get(result.group()));   // fail if bug
  }

  @Override protected void renderHyperLinked(@NotNull Cursor cursor, Doc.@NotNull HyperLinked text, Outer outer) {
    var href = text.href();
    cursor.invisibleContent("<a ");
    if (text.id() != null) cursor.invisibleContent("id=\"" + normalizeId(text.id()) + "\" ");
    if (text.hover() != null) {
      cursor.invisibleContent("class=\"aya-hover\" ");
      cursor.invisibleContent("aya-type=\"" + text.hover() + "\" ");
    }
    cursor.invisibleContent("href=\"");
    cursor.invisibleContent(normalizeHref(href));
    cursor.invisibleContent("\">");
    renderDoc(cursor, text.doc(), Outer.EnclosingTag);
    cursor.invisibleContent("</a>");
  }

  public static @NotNull String normalizeId(@NotNull LinkId linkId) {
    return switch (linkId) {
      case LinkId.DirectLink(var link) -> link;
      case LinkId.LocalId(var id) -> id.fold(DocHtmlPrinter::normalizeQuerySelector, x -> "v" + x);
      // ^ CSS3 selector does not support IDs starting with a digit, so we prefix them with "v".
      // See https://stackoverflow.com/a/37271406/9506898 for more details.
    };
  }

  public static @NotNull String normalizeHref(@NotNull LinkId linkId) {
    return switch (linkId) {
      case LinkId.DirectLink(var link) -> link;
      case LinkId.LocalId localId -> "#" + normalizeId(localId);
    };
  }

  /**
   * <a href="https://stackoverflow.com/a/45519999/9506898">Thank you!</a>
   * <a href="https://jkorpela.fi/ucs.html8">ISO 10646 character listings</a>
   * <p>
   * In CSS, identifiers (including element names, classes, and IDs in selectors)
   * can contain only the characters [a-zA-Z0-9] and ISO 10646 characters U+00A0 and higher,
   * plus the hyphen (-) and the underscore (_);
   * they cannot start with a digit, two hyphens, or a hyphen followed by a digit.
   * Identifiers can also contain escaped characters and any ISO 10646 character as a numeric code (see next item).
   * For instance, the identifier "B&W?" may be written as "B\&W\?" or "B\26 W\3F".
   */
  public static @NotNull String normalizeQuerySelector(@NotNull String selector) {
    selector = selector.replaceAll("::", "-"); // note: scope::name -> scope-name
    // Java's `Pattern` assumes only ASCII text are matched, where `T²` will be incorrectly normalize to "T?".
    // But according to CSS3, `T²` is a valid identifier.
    var builder = new StringBuilder();
    for (var c : selector.toCharArray()) {
      if (Character.isLetterOrDigit(c) || c >= 0x00A0 || c == '-' || c == '_')
        builder.append(c);
      else builder.append(Integer.toHexString(c));
    }
    return builder.toString();
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
