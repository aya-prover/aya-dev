// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.backend.latex;

import org.aya.pretty.backend.string.StringPrinter;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public class DocTeXPrinter extends StringPrinter<TeXPrinterConfig> {
  @Override protected void renderHeader() {
    builder.append("\\begin{tabular}{lr}");
  }

  @Override protected void renderFooter() {
    builder.append("\\end{tabular}");
  }

  @Override protected void renderPlainText(@NotNull String content) {
    super.renderPlainText(content.replace("\\", ""));
  }

  @Override protected void renderIndent(int indent) {
    builder.append("~".repeat(indent));
  }

  @Override protected void renderLineStart() {
    builder.append("&");
  }

  @Override protected void renderHardLineBreak() {
    builder.append("\\\\\n");
  }
}
