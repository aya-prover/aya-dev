// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.console;

import org.aya.cli.parse.AyaParserImpl;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.pretty.doc.Doc;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.ThrowingReporter;
import org.ice1000.jimgui.JImGui;
import org.ice1000.jimgui.NativeBool;
import org.ice1000.jimgui.NativeInt;
import org.ice1000.jimgui.cpp.DeallocatableObjectManager;
import org.ice1000.jimgui.util.JniLoader;

public interface WidthPreviewToy {
  static void main(String[] args) {
    var ayaParser = new AyaParserImpl(new ThrowingReporter(AyaPrettierOptions.informative()));
    var doc = ayaParser.expr("""
      do {
        x <- xs,
        y <- ys,
        return (x + y)
      }
      """, SourcePos.NONE).toDoc(AyaPrettierOptions.pretty());
    preview(doc, 30);
  }

  static void preview(Doc doc, int maxWidth) {
    JniLoader.load();
    //noinspection MismatchedQueryAndUpdateOfCollection
    try (var imgui = new JImGui();
         var manager = new DeallocatableObjectManager()) {
      var width = new NativeInt();
      width.modifyValue(maxWidth);
      var unicode = new NativeBool();
      manager.add(width);
      manager.add(unicode);
      while (!imgui.windowShouldClose()) {
        imgui.initNewFrame();
        imgui.text(doc.renderToString(width.accessValue(), unicode.accessValue()));
        imgui.sliderInt("Width", width, 1, maxWidth);
        imgui.checkbox("Unicode", unicode);
        imgui.render();
      }
    }
  }
}
