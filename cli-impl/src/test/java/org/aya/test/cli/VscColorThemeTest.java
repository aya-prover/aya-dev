// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.cli;

import org.aya.cli.render.vscode.ColorTheme;
import org.aya.pretty.style.AyaStyleKey;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VscColorThemeTest {
  public final static Path TEST_DATA = Path.of("./src/test/resources/dark_plus.json");

  @Test public void test() {
    var colorTheme = ColorTheme.loadFrom(TEST_DATA).getOrThrow();
    var colorScheme = colorTheme.buildColorScheme(null);
    var colors = colorScheme.definedColors();

    assertEquals(0x569CD6, colors.get(AyaStyleKey.Keyword.key()));
    assertEquals(0xDCDCAA, colors.get(AyaStyleKey.Fn.key()));
    assertEquals(0x4EC9B0, colors.get(AyaStyleKey.Generalized.key()));
    assertEquals(0x4EC9B0, colors.get(AyaStyleKey.Data.key()));
    assertEquals(0x4EC9B0, colors.get(AyaStyleKey.Clazz.key()));
    assertEquals(0xDCDCAA, colors.get(AyaStyleKey.Con.key()));
    assertEquals(0xDCDCAA, colors.get(AyaStyleKey.Member.key()));
  }
}
