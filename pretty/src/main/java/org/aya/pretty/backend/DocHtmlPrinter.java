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
      <head>
      <meta charset="UTF-8" />
      <meta name="viewport" content="width=device-width, initial-scale=1.0" />
      </head>
      <body>
      <pre>
      """);
  }

  @Override
  protected void renderFooter() {
    builder.append("""
      </pre>
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
      renderDoc(text.doc());
      builder.append("</a>");
    } else {
      super.renderHyperLinked(text);
    }
  }

  @Override
  protected void renderHardLineBreak() {
    builder.append("<br>");
  }
}
