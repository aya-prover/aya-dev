// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.latex;

import kala.collection.Map;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.pretty.backend.string.Cursor;
import org.aya.pretty.backend.string.StringPrinter;
import org.aya.pretty.backend.string.StringPrinterConfig;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public class DocTeXPrinter extends StringPrinter<DocTeXPrinter.Config> {
  @Override protected void renderHeader(@NotNull Cursor cursor) {
    cursor.invisibleContent("\\noindent");
  }

  @Override protected @NotNull String escapePlainText(@NotNull String content, Outer outer) {
    // TODO: escape according to `outer`
    return content.replace("\\", "").replace("_", "\\_");
  }

  private static @NotNull Tuple2<String, String> id(@NotNull String name) {
    return Tuple.of(name, name);
  }

  private static final @NotNull Map<String, String> commandMapping = Map.ofEntries(
    Tuple.of("Pi", "\\Pi"),
    Tuple.of("Sig", "\\Sigma"),
    Tuple.of("\\", "\\lambda"),
    Tuple.of("\\/", "\\lor"),
    Tuple.of("/\\", "\\land"),
    Tuple.of("|", "\\mid"),
    Tuple.of("=>", "\\Rightarrow"),
    Tuple.of("->", "\\to"),
    Tuple.of("_|_", "\\bot"),
    Tuple.of("~", "\\neg"),
    Tuple.of("**", "\\times"),
    id(":"), id("."),
    id(":="),
    id("("), id(")"),
    id("[|"), id("|]"),
    Tuple.of("{", "\\{"),
    Tuple.of("}", "\\}")
  );

  @Override protected void renderSpecialSymbol(@NotNull Cursor cursor, @NotNull String text, Outer outer) {
    for (var k : commandMapping.keysView()) {
      if (text.equals(k)) {
        cursor.invisibleContent("$");
        cursor.visibleContent(commandMapping.get(k));
        cursor.invisibleContent("$");
        return;
      }
    }
    System.err.println("Warn: unknown symbol " + text);
    renderPlainText(cursor, text, outer);
  }

  @Override public @NotNull String makeIndent(int indent) {
    if (indent == 0) return "";
    return "\\hspace*{" + indent * 0.5 + "em}";
  }

  @Override protected void renderHardLineBreak(@NotNull Cursor cursor, @NotNull Outer outer) {
    cursor.lineBreakWith("\\\\\n");
  }

  @Override protected void renderInlineCode(@NotNull Cursor cursor, Doc.@NotNull InlineCode code, Outer outer) {
    cursor.invisibleContent("\\fbox{");
    renderDoc(cursor, code.code(), outer);
    cursor.invisibleContent("}");
  }

  @Override
  protected void renderList(@NotNull Cursor cursor, Doc.@NotNull List list, @NotNull Outer outer) {
    var env = list.isOrdered() ? "enumerate" : "itemize";
    cursor.invisibleContent("\\begin{" + env + "}");
    list.items().forEach(item -> {
      cursor.invisibleContent("\\item ");
      renderDoc(cursor, item, outer);
      // TODO: we are in both Outer.List and Outer.EnclosingTag
    });
    cursor.invisibleContent("\\end{" + env + "}");
  }

  /**
   * @author ice1000
   */
  public static class Config extends StringPrinterConfig<TeXStylist> {
    public Config() {
      this(TeXStylist.DEFAULT);
    }

    public Config(@NotNull TeXStylist stylist) {
      super(stylist, INFINITE_SIZE, false);
    }
  }
}
