// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.latex;

import kala.collection.Map;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.pretty.backend.string.Cursor;
import org.aya.pretty.backend.string.StringPrinter;
import org.aya.pretty.backend.string.StringPrinterConfig;
import org.aya.pretty.backend.string.StringStylist;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

import static org.aya.pretty.backend.string.StringPrinterConfig.StyleOptions.*;

/**
 * @author ice1000
 */
public class DocTeXPrinter extends StringPrinter<DocTeXPrinter.Config> {
  @Override protected void renderHeader(@NotNull Cursor cursor) {
    if (config.opt(HeaderCode, false)) {
      // cursor.invisibleContent("\\noindent{}");
      // This prevents us from using \raggedright followed by a \setlength\parindent.
      // We should expect users to deal with indentations themselves.
      renderStyleCommand(cursor);
    }
  }

  protected void renderStyleCommand(@NotNull Cursor cursor) {
    if (!config.separateStyle()) return;
    if (!config.opt(StyleCode, false)) return;
    // colors are converted to `\definecolor` in package xcolor.
    var colors = TeXStylist.colorsToTex(config.getStylist().colorScheme);
    cursor.invisibleContent(colors + "\n");
    // styles are converted to `\newcommand`.
    config.getStylist().styleFamily.definedStyles().forEach((name, style) -> {
      var cmdName = TeXStylist.styleKeyToTex(name);
      var cmd = TeXStylist.stylesToTexCmd(style, "#1");
      var line = "\\newcommand\\%s[1]{%s}".formatted(cmdName, cmd);
      cursor.invisibleContent(line + "\n");
    });
  }

  @Override protected @NotNull StringStylist prepareStylist() {
    return config.separateStyle() ? new TeXStylist.ClassedPreset(config.getStylist()) : super.prepareStylist();
  }

  @Override protected @NotNull String escapePlainText(@NotNull String content, EnumSet<Outer> outer) {
    // `Outer.Code` means we are in the minted environment --- no need to escape
    if (outer.contains(Outer.Code)) return content;
    // TODO: math mode escape?
    if (outer.contains(Outer.Math)) return content;
    return content.replace("\\", "")
      .replace("_", "\\textunderscore{}")
      // This is a stupid hack. Maybe we can calculate consecutive spaces
      .replace("  ", makeIndent(2))
      .replace(" ", makeIndent(1));
  }

  private static @NotNull Tuple2<String, String> id(@NotNull String name) {
    return Tuple.of(name, name);
  }

  /** similar to StringPrinter, but with mappings from source code unicode to LaTeX unicode. */
  private static final @NotNull Map<String, String> commandMapping = Map.ofEntries(
    Tuple.of("Sig", "\\Sigma"),
    Tuple.of("\\", "\\backslash"),
    Tuple.of("\\/", "\\lor"),
    Tuple.of("/\\", "\\land"),
    Tuple.of("|", "\\mid"),
    Tuple.of("=>", "\\Rightarrow"),
    Tuple.of("\u2191", "\\uparrow"),
    Tuple.of("->", "\\to"),
    Tuple.of("_|_", "\\bot"),
    Tuple.of("forall", "\\forall"),
    Tuple.of("\u03A3", "\\Sigma"),
    Tuple.of("\u2228", "\\lor"),
    Tuple.of("\u2227", "\\land"),
    Tuple.of("\u21D2", "\\Rightarrow"),
    Tuple.of("\u2192", "\\to"),
    Tuple.of("\u22A5", "\\bot"),
    Tuple.of("\u2200", "\\forall"),
    Tuple.of("_", "\\_"),
    Tuple.of("~", "\\neg"),
    Tuple.of("**", "\\times"),
    id("(|"), id("|)"),
    Tuple.of("\u2987", "(|"),
    Tuple.of("\u2988", "|)"),
    id("[|"), id("|]"),
    Tuple.of("\u27E6", "[|"),
    Tuple.of("\u27E7", "|]"),
    Tuple.of("{|", "\\{|"),
    Tuple.of("|}", "|\\}"),
    Tuple.of("\u2983", "\\{|"),
    Tuple.of("\u2984", "|\\}"),
    id(":"), id("."), id(":="), id("="),
    id("("), id(")"),
    Tuple.of("{", "\\{"),
    Tuple.of("}", "\\}")
  );

  @Override protected void renderSpecialSymbol(@NotNull Cursor cursor, @NotNull String text, EnumSet<Outer> outer) {
    for (var k : commandMapping.keysView()) {
      if (text.equals(k)) {
        if (!config.katex()) cursor.invisibleContent("\\(");
        cursor.visibleContent(commandMapping.get(k));
        if (!config.katex()) cursor.invisibleContent("\\)");
        return;
      }
    }
    if (!text.chars().allMatch(Character::isLetter)) System.err.println("Warn: unknown symbol " + text);
    renderPlainText(cursor, text, outer);
  }

  @Override @NotNull protected String makeIndent(int indent) {
    if (indent == 0) return "";
    return "\\hspace{" + indent * 0.5 + "em}";
  }

  @Override protected void renderHardLineBreak(@NotNull Cursor cursor, EnumSet<Outer> outer) {
    if (outer.contains(Outer.List))
      cursor.lineBreakWith("\n"); // list items are separated by source code new line `\n`
    else
      cursor.lineBreakWith("~\\\\\n"); // LaTeX uses `\\` for printed line breaks.
  }

  @Override
  protected void renderInlineCode(@NotNull Cursor cursor, Doc.@NotNull InlineCode code, EnumSet<Outer> outer) {
    formatInline(cursor, code.code(), "\\texttt{", "}", outer);
    // ^ `Outer.Code` is only for minted. Do not switch to code mode.
  }

  @Override protected void renderCodeBlock(@NotNull Cursor cursor, Doc.@NotNull CodeBlock code, EnumSet<Outer> outer) {
    separateBlockIfNeeded(cursor, outer);
    if (code.language().isAya())
      renderDoc(cursor, code.code(), outer); // `Outer.Code` is only for minted. Do not switch to code mode.
    else
      formatBlock(cursor, code.code(),
        "\\begin{minted}[%s]".formatted(code.language().displayName().toLowerCase()),
        "\\end{minted}",
        EnumSet.of(Outer.Code)
      );
  }

  @Override
  protected void renderInlineMath(@NotNull Cursor cursor, Doc.@NotNull InlineMath code, EnumSet<Outer> outer) {
    formatInline(cursor, code.formula(), "$", "$", EnumSet.of(Outer.Math));
  }

  @Override protected void renderMathBlock(@NotNull Cursor cursor, Doc.@NotNull MathBlock block, EnumSet<Outer> outer) {
    separateBlockIfNeeded(cursor, outer);
    formatBlock(cursor, block.formula(), "\\[", "\\]", EnumSet.of(Outer.Math));
  }

  @Override protected void renderList(@NotNull Cursor cursor, Doc.@NotNull List list, EnumSet<Outer> outer) {
    var env = list.isOrdered() ? "enumerate" : "itemize";
    separateBlockIfNeeded(cursor, outer);
    formatBlock(cursor, "\\begin{" + env + "}", "\\end{" + env + "}", outer, () ->
      formatList(cursor, list, idx -> "\\item", outer));
  }

  /**
   * @author ice1000
   */
  public static class Config extends StringPrinterConfig<TeXStylist> {
    public Config() {
      this(TeXStylist.DEFAULT);
    }

    public boolean katex() {
      return getStylist().isKaTeX;
    }

    public boolean separateStyle() {
      return opt(SeparateStyle, false) && !katex();
    }

    public Config(@NotNull TeXStylist stylist) {
      super(stylist);
    }
  }
}
