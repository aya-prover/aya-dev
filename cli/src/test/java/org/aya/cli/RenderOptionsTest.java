// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli;

import org.aya.cli.repl.ReplConfig;
import org.aya.cli.repl.render.RenderOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RenderOptionsTest {
  @Test public void serde() {
    var gson = ReplConfig.newGsonBuilder().create();
    var json = gson.toJson(RenderOptions.CLI_DEFAULT);
    assertEquals(
      RenderOptions.CLI_DEFAULT,
      gson.fromJson(json, RenderOptions.class));
  }
}
