// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.cli;

import com.google.gson.GsonBuilder;
import org.aya.cli.render.RenderOptions;
import org.aya.cli.utils.LiteratePrettierOptions;
import org.aya.pretty.doc.Doc;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RenderOptionsTest {
  @Test public void serde() {
    var gson = LiteratePrettierOptions.gsonBuilder(new GsonBuilder()).create();
    var json = gson.toJson(new RenderOptions());
    assertEquals(
      new RenderOptions(),
      gson.fromJson(json, RenderOptions.class));
  }

  @Test public void target() {
    var doc = Doc.code("hello");
    var opt = new RenderOptions();
    opt.checkDeserialization();
    var opts = new RenderOptions.DefaultSetup(false, false, false, true, -1, false);
    assertEquals("`hello'", opt.render(RenderOptions.OutputTarget.Unix, doc, opts));
    assertEquals("`hello'", opt.render(RenderOptions.OutputTarget.ANSI16, doc, opts));
    assertEquals("\\texttt{hello}", opt.render(RenderOptions.OutputTarget.LaTeX, doc, opts));
    assertEquals("\\texttt{hello}", opt.render(RenderOptions.OutputTarget.KaTeX, doc, opts));
    assertEquals("<code class=\"Aya\">hello</code>", opt.render(RenderOptions.OutputTarget.HTML, doc, opts));
    assertEquals("`hello`", opt.render(RenderOptions.OutputTarget.Plain, doc, opts));
  }
}
