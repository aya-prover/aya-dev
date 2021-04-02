// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.backend.latex;

import org.aya.pretty.backend.string.Cursor;
import org.aya.pretty.backend.string.StringPrinter;
import org.glavo.kala.collection.Map;
import org.glavo.kala.tuple.Tuple;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public class DocTeXPrinter extends StringPrinter<TeXPrinterConfig> {
  @Override protected void renderHeader() {
    builder.append("\\begin{tabular}{ll}\n&");
  }

  @Override protected void renderFooter() {
    builder.append("\n\\end{tabular}");
  }

  @Override protected void renderPlainText(@NotNull Cursor cursor, @NotNull String content) {
    super.renderPlainText(cursor, content
      .replace("\\", "")
      .replace("_", "\\_"));
  }

  private static final @NotNull Map<String, String> commandMapping = Map.ofEntries(
    Tuple.of("\\Pi", "\\Pi"),
    Tuple.of("\\Sig", "\\Sigma"),
    Tuple.of("=>", "\\Rightarrow"),
    Tuple.of("->", "\\to"),
    Tuple.of("{", "\\{"),
    Tuple.of("}", "\\}")
  );

  @Override protected void renderSpecialSymbol(@NotNull Cursor cursor, @NotNull String text) {
    for (var k : commandMapping.keysView()) {
      if (text.contains(k)) {
        builder.append(" $");
        var str = commandMapping.get(k);
        cursor.visibleContent(() -> builder.append(str));
        builder.append("$ ");
        return;
      }
    }
    super.renderSpecialSymbol(cursor, text);
  }

  @Override protected void renderIndent(@NotNull Cursor cursor, int indent) {
    if (indent > 0) builder.append("\\hspace*{").append(indent * 0.5).append("em}");
    cursor.indent(indent);
  }

  @Override protected void renderLineStart(@NotNull Cursor cursor) {
    builder.append("&");
  }

  @Override protected void renderHardLineBreak() {
    builder.append("\\\\\n");
  }
}
