// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp;

import kala.collection.immutable.ImmutableMap;
import kala.collection.mutable.MutableMap;
import org.aya.lsp.models.ServerOptions;
import org.aya.lsp.options.RenderOptions;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.pretty.style.AyaColorScheme;
import org.aya.pretty.style.AyaStyleFamily;
import org.javacs.lsp.MarkupKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class LspRenderTest extends LspTesterBase {
  @Test
  public void testParseColor() {
    var code0 = "#0B45E0";
    var code1 = "0x19198A";
    var code2 = "000000";

    assertEquals(0x0B45E0, RenderOptions.parseColor(code0).get());
    assertEquals(0x19198A, RenderOptions.parseColor(code1).get());
    assertEquals(0x000000, RenderOptions.parseColor(code2).get());

    var badCode0 = "#0B45E";
    var badCode1 = "0x1919810";
    var badCode2 = "IMKIVA";

    assertFalse(RenderOptions.parseColor(badCode0).isOk());
    assertFalse(RenderOptions.parseColor(badCode1).isOk());
    assertFalse(RenderOptions.parseColor(badCode2).isOk());
  }

  @Test
  public void testFromOptions() {
    var opt0 = new ServerOptions(
      null, null, null
    );

    var expected0 = new RenderOptions(
      AyaColorScheme.EMPTY,
      AyaStyleFamily.DEFAULT
    );

    var opt1 = new ServerOptions(
      "emacs", null, null
    );

    var expected1 = new RenderOptions(
      AyaColorScheme.EMACS,
      AyaStyleFamily.DEFAULT
    );

    var opt2 = new ServerOptions(
      "IntelliJ", Map.of(), null
    );

    var expected2 = new RenderOptions(
      AyaColorScheme.INTELLIJ,
      AyaStyleFamily.DEFAULT
    );

    var opt3 = new ServerOptions(
      "intellij", ImmutableMap.of(
        AyaColorScheme.Key.DataCall.key(), "0x0B45E0",
        AyaColorScheme.Key.StructCall.key(), "0x19198A"
      ).asJava(),
      null
    );

    var expected3Inner = MutableMap.from(AyaColorScheme.INTELLIJ.definedColors());
    expected3Inner.put(AyaColorScheme.Key.DataCall.key(), 0x0B45E0);
    expected3Inner.put(AyaColorScheme.Key.StructCall.key(), 0x19198A);

    var expected3 = new RenderOptions(
      new AyaColorScheme(expected3Inner),
      AyaStyleFamily.DEFAULT
    );

    assertEquals(RenderOptions.DEFAULT, RenderOptions.fromServerOptions(opt0));
    assertEquals(expected1, RenderOptions.fromServerOptions(opt1));
    assertEquals(expected2, RenderOptions.fromServerOptions(opt2));
    assertEquals(expected3, RenderOptions.fromServerOptions(opt3));

    var wrong0 = new ServerOptions(
      "hoshino", null, null
    );

    var wrong1 = new ServerOptions(
      "emacs",
      Map.of("aya:FnCaII", "0x114514"),
      null
    );

    var expected1w = new RenderOptions(
      AyaColorScheme.EMACS,
      AyaStyleFamily.DEFAULT
    );

    var wrong2 = new ServerOptions(
      "emacs",
      Map.of("aya:FnCall", "0x1919810"),
      null
    );

    var expected2w = new RenderOptions(
      AyaColorScheme.EMACS,
      AyaStyleFamily.DEFAULT
    );

    var wrong3 = new ServerOptions(
      "emacses",
      null, null
    );

    var expected3w = new RenderOptions(
      AyaColorScheme.EMPTY,
      AyaStyleFamily.DEFAULT
    );

    assertEquals(RenderOptions.DEFAULT, RenderOptions.fromServerOptions(wrong0));
    assertEquals(expected1w, RenderOptions.fromServerOptions(wrong1));
    assertEquals(expected2w, RenderOptions.fromServerOptions(wrong2));
    assertEquals(expected3w, RenderOptions.fromServerOptions(wrong3));
  }

  @Test
  public void testRender() {
    var renderOpt = new RenderOptions(
      AyaColorScheme.EMACS,
      AyaStyleFamily.DEFAULT
    );

    var cap0 = List.of(MarkupKind.PlainText);
    var cap1 = List.of(MarkupKind.Markdown);
    var code = Doc.cat(
      Doc.plain("plaintext"),
      Doc.symbol(", "),
      Doc.styled(Style.color(AyaColorScheme.Key.Keyword.key()), "keyword"));

    var result0 = renderOpt.renderWithCapabilities(code, cap0);
    var result1 = renderOpt.renderWithCapabilities(code, cap1);

    assertEquals("plaintext, keyword", result0);
    assertEquals("<pre class=\"Aya\">plaintext, <span style=\"color:#ff6d00;\">keyword</span></pre>", result1);
  }
}
