// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp;

import org.aya.lsp.options.RenderOptions;
import org.junit.jupiter.api.Test;

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
}
