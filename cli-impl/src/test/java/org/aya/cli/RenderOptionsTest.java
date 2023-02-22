// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli;

import org.aya.cli.interactive.ReplConfig;
import org.aya.cli.render.RenderOptions;
import org.aya.pretty.doc.Doc;
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

  @Test public void target() {
    var doc = Doc.code("hello");
    var opt = new RenderOptions();
    opt.checkDeserialization();
    var opts = new RenderOptions.Opts(false, false, false, true, -1);
    assertEquals("`hello'", opt.render(RenderOptions.OutputTarget.Unix, doc, opts));
    assertEquals("\\fbox{hello}", opt.render(RenderOptions.OutputTarget.LaTeX, doc, opts));
    assertEquals("<code class=\"Aya\">hello</code>", opt.render(RenderOptions.OutputTarget.HTML, doc, opts));
    assertEquals("`hello`", opt.render(RenderOptions.OutputTarget.Plain, doc, opts));
  }
}
