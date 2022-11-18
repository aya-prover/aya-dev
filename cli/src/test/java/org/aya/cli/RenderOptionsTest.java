// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli;

import org.aya.cli.render.RenderOptions;
import org.aya.cli.repl.ReplConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RenderOptionsTest {
  @Test public void serde() {
    var gson = ReplConfig.newGsonBuilder().create();
    var json = gson.toJson(new RenderOptions());
    assertEquals(
      new RenderOptions(),
      gson.fromJson(json, RenderOptions.class));
  }
}
