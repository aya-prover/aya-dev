// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.backend.latex;

import kala.collection.Map;
import kala.tuple.Tuple;
import org.aya.pretty.backend.string.Cursor;
import org.aya.pretty.backend.string.StringPrinter;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public class DocTeXPrinter extends StringPrinter<TeXPrinterConfig> {
  @Override protected void renderHeader(@NotNull Cursor cursor) {
    cursor.invisibleContent("\\begin{tabular}{ll}\n&");
  }

  @Override protected void renderFooter(@NotNull Cursor cursor) {
    cursor.invisibleContent("\n\\end{tabular}");
  }

  @Override protected void renderPlainText(@NotNull Cursor cursor, @NotNull String content) {
    super.renderPlainText(cursor, content
      .replace("\\", "")
      .replace("_", "\\_"));
  }

  private static final @NotNull Map<String, String> commandMapping = Map.ofEntries(
    Tuple.of("Pi", "\\Pi"),
    Tuple.of("Sig", "\\Sigma"),
    Tuple.of("\\", "\\bda"),
    Tuple.of("=>", "\\Rightarrow"),
    Tuple.of("->", "\\to"),
    Tuple.of("{", "\\{"),
    Tuple.of("}", "\\}")
  );

  @Override protected void renderSpecialSymbol(@NotNull Cursor cursor, @NotNull String text) {
    for (var k : commandMapping.keysView()) {
      if (text.contains(k)) {
        cursor.invisibleContent(" $");
        cursor.visibleContent(commandMapping.get(k));
        cursor.invisibleContent("$ ");
        return;
      }
    }
    super.renderSpecialSymbol(cursor, text);
  }

  @Override public @NotNull String makeIndent(int indent) {
    if (indent == 0) return "";
    return "\\hspace*{" + indent * 0.5 + "em}";
  }

  @Override protected void renderLineStart(@NotNull Cursor cursor) {
    cursor.invisibleContent("&");
  }

  @Override protected void renderHardLineBreak(@NotNull Cursor cursor) {
    cursor.lineBreakWith("\\\\\n");
  }
}
