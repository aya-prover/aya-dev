// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
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

import java.util.EnumSet;
import java.util.regex.Pattern;

import static org.aya.pretty.backend.string.StringPrinterConfig.StyleOptions.*;

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
    if (config.opt(HeaderCode, false)) {
      cursor.invisibleContent(HEAD);
      renderCssStyle(cursor);
      cursor.invisibleContent("</head><body>");
    }
  }

  @Override protected void renderFooter(@NotNull Cursor cursor) {
    if (config.opt(HeaderCode, false)) cursor.invisibleContent("</body></html>");
  }

  protected void renderCssStyle(@NotNull Cursor cursor) {
    if (!config.opt(SeparateStyle, false)) return;
    if (!config.opt(StyleCode, false)) return;
    cursor.invisibleContent("<style>");
    // colors are defined in global scope `:root`
    var colors = Html5Stylist.colorsToCss(config.getStylist().colorScheme);
    cursor.invisibleContent("\n:root {\n%s\n}\n".formatted(colors));
    config.getStylist().styleFamily.definedStyles().forEach((name, style) -> {
      var selector = Html5Stylist.styleKeyToCss(name).map(x -> "." + x).joinToString(" ");
      var css = style.styles().mapNotNull(Html5Stylist::styleToCss).joinToString("\n", "  %s"::formatted);
      var stylesheet = "%s {\n%s\n}\n".formatted(selector, css);
      cursor.invisibleContent(stylesheet);
    });
    cursor.invisibleContent("</style>");
  }

  @Override protected @NotNull StringStylist prepareStylist() {
    return config.opt(SeparateStyle, false) ? new Html5Stylist.ClassedPreset(config.getStylist()) : super.prepareStylist();
  }

  @Override protected @NotNull String escapePlainText(@NotNull String content, EnumSet<Outer> outer) {
    // note: HTML always needs escaping, regardless of `outer`
    return entityPattern.matcher(content).replaceAll(
      result -> entityMapping.get(result.group()));   // fail if bug
  }

  @Override
  protected void renderHyperLinked(@NotNull Cursor cursor, Doc.@NotNull HyperLinked text, EnumSet<Outer> outer) {
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
    renderDoc(cursor, text.doc(), EnumSet.of(Outer.EnclosingTag));
    cursor.invisibleContent("</a>");
  }

  @Override protected void renderImage(@NotNull Cursor cursor, Doc.@NotNull Image image, EnumSet<Outer> outer) {
    cursor.invisibleContent("<img ");
    cursor.invisibleContent("src=\"" + normalizeHref(image.src()) + "\" ");
    cursor.invisibleContent("alt=\"");
    renderDoc(cursor, image.alt(), outer);
    cursor.invisibleContent("\"/>");
  }

  public static @NotNull String normalizeId(@NotNull Link linkId) {
    return switch (linkId) {
      case Link.DirectLink(var link) -> link;
      case Link.LocalId(var id) -> id.fold(Html5Stylist::normalizeCssId, x -> "v" + x);
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

  @Override protected void renderHardLineBreak(@NotNull Cursor cursor, EnumSet<Outer> outer) {
    cursor.lineBreakWith("<br>");
  }

  @Override
  protected void renderInlineCode(@NotNull Cursor cursor, Doc.@NotNull InlineCode code, EnumSet<Outer> outer) {
    // `<code class="" />` is valid, see:
    // https://stackoverflow.com/questions/30748847/is-an-empty-class-attribute-valid-html
    cursor.invisibleContent("<code class=\"" + capitalize(code.language()) + "\">");
    renderDoc(cursor, code.code(), EnumSet.of(Outer.EnclosingTag)); // Even in code mode, we still need to escape
    cursor.invisibleContent("</code>");
  }

  @Override protected void renderCodeBlock(@NotNull Cursor cursor, Doc.@NotNull CodeBlock block, EnumSet<Outer> outer) {
    cursor.invisibleContent("<pre class=\"" + capitalize(block.language()) + "\">");
    renderDoc(cursor, block.code(), EnumSet.of(Outer.EnclosingTag)); // Even in code mode, we still need to escape
    cursor.invisibleContent("</pre>");
  }

  @Override
  protected void renderList(@NotNull Cursor cursor, Doc.@NotNull List list, EnumSet<Outer> outer) {
    var tag = list.isOrdered() ? "ol" : "ul";
    cursor.invisibleContent("<" + tag + ">");
    list.items().forEach(item -> {
      cursor.invisibleContent("<li>");
      renderDoc(cursor, item, EnumSet.of(Outer.List, Outer.EnclosingTag));
      cursor.invisibleContent("</li>");
    });
    cursor.invisibleContent("</" + tag + ">");
  }

  private @NotNull String capitalize(@NotNull org.aya.pretty.doc.Language s) {
    var name = s.displayName();
    return name.isEmpty() ? name : name.substring(0, 1).toUpperCase() + name.substring(1);
  }

  public static class Config extends StringPrinterConfig<Html5Stylist> {
    public Config(@NotNull Html5Stylist stylist) {
      super(stylist);
    }
  }
}
