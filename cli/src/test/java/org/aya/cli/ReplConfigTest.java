// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli;

import kala.control.Option;
import org.aya.cli.repl.ReplConfig;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.generic.util.NormalizeMode;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class ReplConfigTest {
  @Language("JSON")
  public static final @NotNull String TEST_REPL_CONFIG = """
    {
      "prompt": "\\u003e ",
      "normalizeMode": "NF",
      "prettierOptions": {
        "map": {
          "InlineMetas": true,
          "ShowImplicitArgs": false,
          "ShowImplicitPats": true,
          "ShowLambdaTypes": false
        }
      },
      "enableUnicode": true,
      "silent": false,
      "renderOptions": {
        "colorScheme": "Emacs",
        "styleFamily": "Default",
        "path": "dark_plus.json"
      }
    }
    """;

  @Test public void testReplConfig() throws IOException {
    try (var c = ReplConfig.loadFrom(Option.none(), TEST_REPL_CONFIG)) {
      assertEquals("> ", c.prompt);
      assertEquals(NormalizeMode.NF, c.normalizeMode);
      assertTrue(c.enableUnicode);
      assertFalse(c.silent);
      assertTrue(c.prettierOptions.map.get(AyaPrettierOptions.Key.InlineMetas));
      assertTrue(c.prettierOptions.map.get(AyaPrettierOptions.Key.ShowImplicitPats));
      assertFalse(c.prettierOptions.map.get(AyaPrettierOptions.Key.ShowLambdaTypes));
      assertFalse(c.prettierOptions.map.get(AyaPrettierOptions.Key.ShowImplicitArgs));
    }
  }
}
