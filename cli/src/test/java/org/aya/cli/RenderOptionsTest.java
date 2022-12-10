// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli;

import org.aya.cli.render.RenderOptions;
import org.aya.cli.repl.ReplConfig;
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
    assertEquals("`hello'", opt.render(RenderOptions.OutputTarget.Terminal, doc, false, true));
    assertEquals("\\noindent\\fbox{hello}", opt.render(RenderOptions.OutputTarget.LaTeX, doc, false, true));
    assertEquals("<code class=\"Aya\">hello</code>", opt.render(RenderOptions.OutputTarget.HTML, doc, false, true));
    assertEquals("`hello`", opt.render(RenderOptions.OutputTarget.Plain, doc, false, true));
  }
}
