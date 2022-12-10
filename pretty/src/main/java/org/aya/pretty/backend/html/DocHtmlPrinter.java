// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.html;

import kala.collection.immutable.ImmutableMap;
import org.aya.pretty.backend.string.Cursor;
import org.aya.pretty.backend.string.StringPrinter;
import org.aya.pretty.backend.string.StringPrinterConfig;
import org.aya.pretty.backend.string.StringStylist;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Link;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * Html backend, which ignores page width.
 */
public class DocHtmlPrinter<Config extends DocHtmlPrinter.Config> extends StringPrinter<Config> {
  @Language(value = "HTML")
  @NotNull String HEAD = """
    <!DOCTYPE html><html lang="en"><head>
    <title>Aya file</title>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    """ + HtmlConstants.HOVER_ALL_OCCURS + HtmlConstants.HOVER_STYLE + HtmlConstants.HOVER_POPUP_STYLE;

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
    if (config.withHeader) {
      cursor.invisibleContent(HEAD);
      if (config.supportsCssStyle()) renderCssStyle(cursor);
      cursor.invisibleContent("</head><body>");
    }
    cursor.invisibleContent("<pre class=\"Aya\">");
  }

  @Override protected void renderFooter(@NotNull Cursor cursor) {
    cursor.invisibleContent("</pre>");
    if (config.withHeader) cursor.invisibleContent("</body></html>");
  }

  protected void renderCssStyle(@NotNull Cursor cursor) {
    // TODO: css
  }

  @Override protected @NotNull StringStylist prepareStylist() {
    return config.supportsCssStyle() ? new Html5Stylist.ClassedPreset(config.getStylist()) : super.prepareStylist();
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

  public static @NotNull String normalizeId(@NotNull Link linkId) {
    return switch (linkId) {
      case Link.DirectLink(var link) -> link;
      case Link.LocalId(var id) -> id.fold(DocHtmlPrinter::normalizeQuerySelector, x -> "v" + x);
      // ^ CSS3 selector does not support IDs starting with a digit, so we prefix them with "v".
      // See https://stackoverflow.com/a/37271406/9506898 for more details.
    };
  }

  public static @NotNull String normalizeHref(@NotNull Link linkId) {
    return switch (linkId) {
      case Link.DirectLink(var link) -> link;
      case Link.LocalId localId -> "#" + normalizeId(localId);
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

  public static class Config extends StringPrinterConfig<Html5Stylist> {
    public final boolean withHeader;

    /** Set doc style with html "class" attribute and css block */
    public boolean supportsCssStyle() {
      return withHeader;
    }

    public Config(boolean withHeader) {
      this(Html5Stylist.DEFAULT, withHeader);
    }

    public Config(@NotNull Html5Stylist stylist, boolean withHeader) {
      super(stylist, INFINITE_SIZE, true);
      this.withHeader = withHeader;
    }
  }
}
