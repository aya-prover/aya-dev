// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.backend;

import org.aya.pretty.backend.html.HtmlPrinterConfig;
import org.aya.pretty.backend.string.StringLink;
import org.aya.pretty.backend.string.StringOutputPrinter;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

/**
 * Html backend, which ignores page width.
 */
public class DocHtmlPrinter extends StringOutputPrinter<HtmlPrinterConfig> {
  @Override
  protected void renderHeader() {
    builder.append("""
      <!DOCTYPE html>
      <html>
      <head></head>
      <body>
      """);
  }

  @Override
  protected void renderFooter() {
    builder.append("""
      </body>
      </html>
      """);
  }

  @Override
  protected void renderHyperLinked(Doc.@NotNull HyperLinked text) {
    if (text.link() instanceof StringLink link) {
      builder.append("<a href=\"");
      builder.append(link.linkText());
      builder.append("\">");
      builder.append(">");
      renderDoc(text.doc());
      builder.append("</a>");
    } else {
      super.renderHyperLinked(text);
    }
  }

  @Override
  protected void renderHardLineBreak() {
    builder.append("</br>");
  }

  @Override
  protected void renderStyled(Doc.@NotNull Styled styled) {
    super.renderStyled(styled);
  }

  @Override
  protected void renderIndent(int indent) {
    builder.append("&nbsp".repeat(indent));
  }
}
