// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.md;

import org.aya.pretty.backend.html.DocHtmlPrinter;
import org.aya.pretty.backend.html.HtmlConstants;
import org.aya.pretty.backend.string.Cursor;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Language;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.regex.Pattern;

import static org.aya.pretty.backend.string.StringPrinterConfig.StyleOptions.*;

public class DocMdPrinter extends DocHtmlPrinter<DocMdPrinter.Config> {
  public static final Pattern MD_ESCAPE = Pattern.compile("[#&()*+;<>\\[\\\\\\]_`|~]");
  /** `Doc.plain("1. hello")` should not be rendered as a list, see MdStyleTest */
  public static final Pattern MD_ESCAPE_FAKE_LIST = Pattern.compile("(^\\s*\\d+)\\.( |$)", Pattern.MULTILINE);

  @Override protected void renderHeader(@NotNull Cursor cursor) {
  }

  @Override protected void renderFooter(@NotNull Cursor cursor) {
    // put generated styles at the end of the file
    if (config.opt(HeaderCode, false)) {
      if (config.opt(AyaFlavored, false)) {
        cursor.invisibleContent(HtmlConstants.HOVER_STYLE);
        cursor.invisibleContent(HtmlConstants.HOVER_TYPE_POPUP_STYLE);
        cursor.invisibleContent(config.opt(ServerSideRendering, false)
          ? HtmlConstants.HOVER_SSR
          : HtmlConstants.HOVER);
      }
      renderCssStyle(cursor);
    }
  }

  // markdown escape: https://spec.commonmark.org/0.30/#backslash-escapes
  @Override protected @NotNull String escapePlainText(@NotNull String content, EnumSet<Outer> outer) {
    if (outer.contains(Outer.EnclosingTag)) {
      // If we are in HTML tag (like rendered Aya code), use HTML escape settings.
      return super.escapePlainText(content, outer);
    }
    // If we are in Markdown, do not escape text in code block.
    if (outer.contains(Outer.Code) || outer.contains(Outer.Math)) return content;
    // We are not need to call `super.escapePlainText`, we will escape them in markdown way.
    // I wish you can understand this genius regexp
    // What we will escape:
    // .
    // What we won't escape, which are not special characters
    // or don't matter in a plain text (like `:` and `"` work in footnotes only):
    // ":,%$'=@?^{}/-
    // What we should escape, but we don't:
    // `!`: `!` is only used in `![]()`, but we already escape `[`, `]`, `(`, `)`, so `!` doesn't work.
    content = MD_ESCAPE
      .matcher(content)
      .replaceAll(result -> {
        var chara = result.group();
        // special characters, see Matcher#appendReplacement
        if (chara.equals("\\")) chara = "\\\\";
        return "\\\\" + chara;
      });

    // escape fake lists
    content = MD_ESCAPE_FAKE_LIST.matcher(content).replaceAll("$1\\\\.$2");

    return content;
  }

  @Override protected void renderHardLineBreak(@NotNull Cursor cursor, EnumSet<Outer> outer) {
    cursor.lineBreakWith("\n");
  }

  @Override
  protected void renderHyperLinked(@NotNull Cursor cursor, @NotNull Doc.HyperLinked text, EnumSet<Outer> outer) {
    Runnable pureMd = () -> {
      // use markdown typesetting only when the stylist is pure markdown
      var href = text.href();
      cursor.invisibleContent("[");
      renderDoc(cursor, text.doc(), outer);
      cursor.invisibleContent("](");
      cursor.invisibleContent(normalizeHref(href));
      cursor.invisibleContent(")");
      // TODO: text.id(), text.hover()
    };
    runSwitch(pureMd, () -> {
      if (!outer.isEmpty()) super.renderHyperLinked(cursor, text, outer);
        // ^ In AyaMd mode, `outer != Free` (Free means empty) means whether:
        // 1. we are in rendered Aya code block,
        // 2. we are in an HTML tag (like `<a>`).
        // In both cases, markdown typesetting does not work.
      else pureMd.run();
    });
  }

  @Override protected void renderImage(@NotNull Cursor cursor, @NotNull Doc.Image image, EnumSet<Outer> outer) {
    cursor.invisibleContent("![");
    renderDoc(cursor, image.alt(), outer);
    cursor.invisibleContent("](");
    cursor.invisibleContent(normalizeHref(image.src()));
    cursor.invisibleContent(")");
  }

  @Override protected void renderList(@NotNull Cursor cursor, @NotNull Doc.List list, EnumSet<Outer> outer) {
    formatList(cursor, list, outer);
  }

  @Override
  protected void renderInlineMath(@NotNull Cursor cursor, Doc.@NotNull InlineMath code, EnumSet<Outer> outer) {
    formatInline(cursor, code.formula(), "$", "$", EnumSet.of(Outer.Math));
  }

  /**
   * @implNote We don't call {@link #separateBlockIfNeeded}, as Markdown spec says:
   * any block is surrounded with Paragraphs, which is handled in {@link MdStylist#formatCustom}
   * by inserting a blank line to generated source code (just like {@link #separateBlockIfNeeded}).
   */
  @Override protected void renderMathBlock(@NotNull Cursor cursor, Doc.@NotNull MathBlock block, EnumSet<Outer> outer) {
    formatBlock(cursor, block.formula(), "$$", "$$", EnumSet.of(Outer.Math));
  }

  @Override
  protected void renderInlineCode(@NotNull Cursor cursor, @NotNull Doc.InlineCode code, EnumSet<Outer> outer) {
    // assumption: inline code cannot be nested in markdown, but don't assert it.
    Runnable pureMd = () -> formatInline(cursor, code.code(), "`", "`", EnumSet.of(Outer.Code));
    runSwitch(pureMd, () -> {
      if (code.language().isAya()) formatInline(cursor, code.code(),
        "<code class=\"Aya\">", "</code>",
        EnumSet.of(Outer.EnclosingTag));
      else pureMd.run();
    });
  }

  /**
   * @implNote We don't call {@link #separateBlockIfNeeded}, as Markdown spec says:
   * any block is surrounded with Paragraphs, which is handled in {@link MdStylist#formatCustom}
   * by inserting a blank line to generated source code (just like {@link #separateBlockIfNeeded}).
   */
  @Override protected void renderCodeBlock(@NotNull Cursor cursor, @NotNull Doc.CodeBlock block, EnumSet<Outer> outer) {
    // assumption: code block cannot be nested in markdown, but don't assert it.
    var mark = block.language() == Language.Builtin.Markdown ? "~~~" : "```";
    Runnable pureMd = () -> formatBlock(cursor, block.code(),
      mark + block.language().displayName().toLowerCase(), mark,
      EnumSet.of(Outer.Code));
    runSwitch(pureMd,
      () -> {
        if (block.language().isAya()) formatBlock(cursor, "<pre class=\"Aya\">", "</pre>", outer,
          () -> formatInline(cursor, block.code(), "<code>", "</code>", EnumSet.of(Outer.EnclosingTag)));
        else pureMd.run();
      });
  }

  private void runSwitch(@NotNull Runnable pureMd, @NotNull Runnable ayaMd) {
    if (config.opt(AyaFlavored, false)) ayaMd.run();
    else pureMd.run();
  }

  public static class Config extends DocHtmlPrinter.Config {
    public Config(@NotNull MdStylist stylist) {
      super(stylist);
    }
  }
}
