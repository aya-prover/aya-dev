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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
    """ + HtmlConstants.HOVER_STYLE + HtmlConstants.HOVER_TYPE_POPUP_STYLE;

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
      if (config.opt(ServerSideRendering, false)) {
        cursor.invisibleContent(HtmlConstants.HOVER_SSR);
        // TODO: KaTeX server side rendering
      } else {
        cursor.invisibleContent(HtmlConstants.HOVER);
        cursor.invisibleContent(HtmlConstants.KATEX_AUTO_RENDER);
      }
      cursor.invisibleContent("</head><body>\n");
    }
  }

  @Override protected void renderFooter(@NotNull Cursor cursor) {
    if (config.opt(HeaderCode, false)) cursor.invisibleContent("\n</body></html>\n");
  }

  protected void renderCssStyle(@NotNull Cursor cursor) {
    if (!config.opt(SeparateStyle, false)) return;
    if (!config.opt(StyleCode, false)) return;
    cursor.invisibleContent("<style>");
    // colors are defined in global scope `:root`
    var colors = Html5Stylist.colorsToCss(config.getStylist().colorScheme);
    cursor.invisibleContent("\n:root {\n%s\n}\n".formatted(colors));
    config.getStylist().styleFamily.definedStyles().forEach((name, style) -> {
      var selector = Html5Stylist.styleKeyToCss(name).map(x -> STR.".\{x}").joinToString(" ");
      var css = style.styles().mapNotNull(Html5Stylist::styleToCss).joinToString("\n", "  %s"::formatted);
      var stylesheet = "%s {\n%s\n}\n".formatted(selector, css);
      cursor.invisibleContent(stylesheet);
    });
    cursor.invisibleContent("</style>\n");
  }

  @Override protected @NotNull StringStylist prepareStylist() {
    return config.opt(SeparateStyle, false) ? new Html5Stylist.ClassedPreset(config.getStylist()) : super.prepareStylist();
  }

  @Override protected @NotNull String escapePlainText(@NotNull String content, EnumSet<Outer> outer) {
    // HTML always needs escaping, unless we are in KaTeX math mode
    if (outer.contains(Outer.Math)) return content;
    return entityPattern.matcher(content).replaceAll(
      result -> entityMapping.get(result.group()));   // fail if bug
  }

  @Override protected void renderTooltip(@NotNull Cursor cursor, Doc.@NotNull Tooltip tooltip, EnumSet<Outer> outer) {
    var newCursor = new Cursor(this);
    renderDoc(newCursor, tooltip.tooltip().toDoc(), FREE);
    var tip = newCursor.result().toString();
    // ^ note: the tooltip is shown in a popup, which is a new document.
    cursor.invisibleContent("<span class=\"aya-tooltip\" ");
    cursor.invisibleContent("data-tooltip-text=\"");
    cursor.invisibleContent(Base64.getEncoder().encodeToString(tip.getBytes(StandardCharsets.UTF_8)));
    cursor.invisibleContent("\">");
    renderDoc(cursor, tooltip.doc(), EnumSet.of(Outer.EnclosingTag));
    cursor.invisibleContent("</span>");
  }

  @Override
  protected void renderHyperLinked(@NotNull Cursor cursor, Doc.@NotNull HyperLinked text, EnumSet<Outer> outer) {
    var href = text.href();
    cursor.invisibleContent("<a ");
    if (text.id() != null) cursor.invisibleContent(STR."id=\"\{normalizeId(text.id())}\" ");
    if (text.hover() != null) {
      cursor.invisibleContent("class=\"aya-hover\" ");
      cursor.invisibleContent(STR."aya-hover-text=\"\{text.hover()}\" ");
    }
    cursor.invisibleContent("href=\"");
    cursor.invisibleContent(normalizeHref(href));
    cursor.invisibleContent("\">");
    renderDoc(cursor, text.doc(), EnumSet.of(Outer.EnclosingTag));
    cursor.invisibleContent("</a>");
  }

  @Override protected void renderImage(@NotNull Cursor cursor, Doc.@NotNull Image image, EnumSet<Outer> outer) {
    cursor.invisibleContent("<img ");
    cursor.invisibleContent(STR."src=\"\{normalizeHref(image.src())}\" ");
    cursor.invisibleContent("alt=\"");
    renderDoc(cursor, image.alt(), outer);
    cursor.invisibleContent("\"/>");
  }

  public @NotNull String normalizeId(@NotNull Link linkId) {
    return switch (linkId) {
      case Link.CrossLink(var path, var loc) -> {
        if (path.isEmpty()) yield loc == null ? "" : normalizeId(loc);
        var prefix = config.opt(StringPrinterConfig.LinkOptions.CrossLinkPrefix, "/");
        var postfix = config.opt(StringPrinterConfig.LinkOptions.CrossLinkPostfix, ".html");
        var sep = config.opt(StringPrinterConfig.LinkOptions.CrossLinkSeparator, "/");
        yield path.joinToString(sep, prefix, postfix) + (loc == null ? "" : STR."#\{normalizeId(loc)}");
      }
      case Link.DirectLink(var link) -> link;
      case Link.LocalId(var id) -> id.fold(Html5Stylist::normalizeCssId, x -> STR."v\{x}");
      // ^ CSS3 selector does not support IDs starting with a digit, so we prefix them with "v".
      // See https://stackoverflow.com/a/37271406/9506898 for more details.
    };
  }

  public @NotNull String normalizeHref(@NotNull Link linkId) {
    return switch (linkId) {
      case Link.CrossLink link -> normalizeId(link);
      case Link.DirectLink(var link) -> link;
      case Link.LocalId localId -> STR."#\{normalizeId(localId)}";
    };
  }

  @Override protected void renderHardLineBreak(@NotNull Cursor cursor, EnumSet<Outer> outer) {
    cursor.lineBreakWith("<br>");
  }

  @Override
  protected void renderInlineCode(@NotNull Cursor cursor, Doc.@NotNull InlineCode code, EnumSet<Outer> outer) {
    // `<code class="" />` is valid, see:
    // https://stackoverflow.com/questions/30748847/is-an-empty-class-attribute-valid-html
    cursor.invisibleContent(STR."<code class=\"\{capitalize(code.language())}\">");
    renderDoc(cursor, code.code(), EnumSet.of(Outer.EnclosingTag)); // Even in code mode, we still need to escape
    cursor.invisibleContent("</code>");
  }

  @Override protected void renderCodeBlock(@NotNull Cursor cursor, Doc.@NotNull CodeBlock block, EnumSet<Outer> outer) {
    cursor.invisibleContent(STR."<pre class=\"\{capitalize(block.language())}\">");
    renderDoc(cursor, block.code(), EnumSet.of(Outer.EnclosingTag)); // Even in code mode, we still need to escape
    cursor.invisibleContent("</pre>");
  }

  @Override
  protected void renderInlineMath(@NotNull Cursor cursor, Doc.@NotNull InlineMath code, EnumSet<Outer> outer) {
    // https://katex.org/docs/autorender.html
    formatInline(cursor, code.formula(), "<span class=\"doc-katex-input\">\\(", "\\)</span>", EnumSet.of(Outer.Math));
  }

  @Override protected void renderMathBlock(@NotNull Cursor cursor, Doc.@NotNull MathBlock block, EnumSet<Outer> outer) {
    cursor.invisibleContent("<pre><div class=\"doc-katex-input\">");
    // https://katex.org/docs/autorender.html
    formatBlock(cursor, block.formula(), "\\[", "\\]", EnumSet.of(Outer.Math));
    cursor.invisibleContent("</div></pre>");
  }

  @Override
  protected void renderList(@NotNull Cursor cursor, Doc.@NotNull List list, EnumSet<Outer> outer) {
    var tag = list.isOrdered() ? "ol" : "ul";
    cursor.invisibleContent(STR."<\{tag}>");
    list.items().forEach(item -> {
      cursor.invisibleContent("<li>");
      renderDoc(cursor, item, EnumSet.of(Outer.List, Outer.EnclosingTag));
      cursor.invisibleContent("</li>");
    });
    cursor.invisibleContent(STR."</\{tag}>");
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
