// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli;

import org.aya.cli.render.vscode.ColorTheme;
import org.aya.pretty.style.AyaColorScheme;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VscColorThemeTest {
  public final static Path TEST_DATA = Path.of("./src/test/resources/dark_plus.json");

  @Test
  public void test() {
    var colorTheme = ColorTheme.loadFrom(TEST_DATA).getOrThrow();
    var colorScheme = colorTheme.buildColorScheme(null);
    var colors = colorScheme.definedColors();

    assertEquals(0x569CD6, colors.get(AyaColorScheme.Key.Keyword.key()));
    assertEquals(0xDCDCAA, colors.get(AyaColorScheme.Key.FnCall.key()));
    assertEquals(0x4EC9B0, colors.get(AyaColorScheme.Key.Generalized.key()));
    assertEquals(0x4EC9B0, colors.get(AyaColorScheme.Key.DataCall.key()));
    assertEquals(0x4EC9B0, colors.get(AyaColorScheme.Key.StructCall.key()));
    assertEquals(0xDCDCAA, colors.get(AyaColorScheme.Key.ConCall.key()));
    assertEquals(0xDCDCAA, colors.get(AyaColorScheme.Key.FieldCall.key()));
  }
}
