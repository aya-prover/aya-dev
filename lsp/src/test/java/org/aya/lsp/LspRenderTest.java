// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp;

import kala.collection.immutable.ImmutableMap;
import kala.collection.mutable.MutableMap;
import kala.control.Option;
import org.aya.lsp.models.ServerOptions;
import org.aya.lsp.options.RenderOptions;
import org.aya.pretty.style.AyaColorScheme;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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
      Option.some(AyaColorScheme.EMPTY),
      Option.none()
    );

    var opt1 = new ServerOptions(
      "emacs", null, null
    );

    var expected1 = new RenderOptions(
      Option.some(AyaColorScheme.EMACS),
      Option.none()
    );

    var opt2 = new ServerOptions(
      "intellij", Map.of(), null
    );

    var expected2 = new RenderOptions(
      Option.some(AyaColorScheme.INTELLIJ),
      Option.none()
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
      Option.some(new AyaColorScheme(expected3Inner)),
      Option.none()
    );

    var opt4 = new ServerOptions(
      "intellij", Map.of(
        AyaColorScheme.Key.DataCall.key(), "0x000000",
        AyaColorScheme.Key.StructCall.key(), "0x000000"
      ), null
    );

    var expected4 = new RenderOptions(Option.some(AyaColorScheme.INTELLIJ), Option.none());

    assertSame(expected0.colorScheme().get(), RenderOptions.fromServerOptions(opt0).get().colorScheme().get());
    assertSame(expected1.colorScheme().get(), RenderOptions.fromServerOptions(opt1).get().colorScheme().get());
    assertSame(expected2.colorScheme().get(), RenderOptions.fromServerOptions(opt2).get().colorScheme().get());
    assertEquals(expected3, RenderOptions.fromServerOptions(opt3).get());
    assertSame(expected4.colorScheme().get(), RenderOptions.fromServerOptions(opt4).get().colorScheme().get());

    var wrong0 = new ServerOptions(
      "hoshino", null, null
    );

    var wrong1 = new ServerOptions(
      "emacs",
      Map.of("aya:FnCaII", "0x114514"),
      null
    );

    var wrong2 = new ServerOptions(
      "emacs",
      Map.of("aya:FnCall", "0x1919810"),
      null
    );

    assertFalse(RenderOptions.fromServerOptions(wrong0).isOk());
    assertFalse(RenderOptions.fromServerOptions(wrong1).isOk());
    assertFalse(RenderOptions.fromServerOptions(wrong2).isOk());
  }
}
