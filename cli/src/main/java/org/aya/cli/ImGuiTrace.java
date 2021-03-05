// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.cli;

import org.aya.api.error.SourcePos;
import org.aya.tyck.trace.Trace;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.tuple.Unit;
import org.ice1000.jimgui.*;
import org.ice1000.jimgui.util.JImGuiUtil;
import org.ice1000.jimgui.util.JniLoader;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Objects;

@SuppressWarnings("AccessStaticViaInstance")
public class ImGuiTrace implements Trace.Visitor<JImGui, Unit> {
  public static final int PAGE_WIDTH = 114514;
  private final String sourceCode;
  private @NotNull SourcePos pos;

  public ImGuiTrace(@NotNull String sourceCode) {
    this.sourceCode = sourceCode;
    pos = SourcePos.NONE;
  }

  private @NotNull JImVec4 color(Color color) {
    return new MutableJImVec4(color.getRed() / 256f,
      color.getGreen() / 256f,
      color.getBlue() / 256f,
      color.getAlpha() / 256f);
  }

  public void mainLoop(@NotNull Seq<@NotNull Trace> root) {
    JniLoader.load();
    var imGui = new JImGui();
    imGui.pushStyleVar(JImStyleVars.ItemSpacing, 0f, 2f);
    imGui.getStyle().scaleAllSizes(2);
    try (var config = new JImFontConfig()) {
      var fontAtlas = imGui.getIO().getFonts();
      fontAtlas.clearFonts();
      config.setSizePixels(26);
      fontAtlas.addDefaultFont(config);
    }
    JImGuiUtil.cacheStringToBytes();
    var highlight = color(Color.GREEN);
    while (!imGui.windowShouldClose()) {
      imGui.initNewFrame();
      if (imGui.begin("Source code")) {
        sourceCode(imGui, highlight);
        imGui.end();
      }
      if (imGui.begin("Trace")) {
        root.forEach(e -> e.accept(this, imGui));
        imGui.end();
      }
      imGui.render();
    }
    highlight.deallocateNativeObject();
    imGui.deallocateNativeObject();
  }

  private void sourceCode(JImGui imGui, JImVec4 highlight) {
    var buffer = new StringBuilder();
    var previousContains = false;
    for (var i = 0; i < sourceCode.length(); i++) {
      var c = sourceCode.charAt(i);
      buffer.append(c);
      var isEOL = c == '\n';
      var contains = pos.contains(i + 1);
      if (contains != previousContains) {
        if (!contains) imGui.textColored(highlight, buffer.toString());
        else imGui.text(buffer.toString());
        buffer.delete(0, buffer.length());
      }
      previousContains = contains;
      if (isEOL) {
        imGui.text(buffer.toString());
        buffer.delete(0, buffer.length());
        imGui.newLine();
      } else {
        imGui.sameLine();
      }
    }
  }

  @Override public Unit visitExpr(Trace.@NotNull ExprT t, JImGui imGui) {
    var term = t.term();
    var s = t.expr().toDoc().renderWithPageWidth(PAGE_WIDTH)
      + (term == null ? "" : " : " + term.toDoc().renderWithPageWidth(PAGE_WIDTH))
      + "##" + Objects.hash(t);
    var color = term == null ? Color.CYAN : Color.YELLOW;
    visitSub(s, color, imGui, t.subtraces(), () -> pos = t.expr().sourcePos());
    return Unit.unit();
  }

  private void visitSub(
    String s, Color color, JImGui imGui,
    Buffer<@NotNull Trace> subtraces,
    @NotNull Runnable callback) {
    final var vec4 = color(color);
    imGui.pushStyleColor(JImStyleColors.Text, vec4);
    var node = imGui.treeNode(s);
    if (imGui.isItemHovered()) callback.run();
    if (node) {
      subtraces.forEach(e -> e.accept(this, imGui));
      imGui.treePop();
    }
    imGui.popStyleColor();
    vec4.deallocateNativeObject();
  }

  @Override public Unit visitUnify(Trace.@NotNull UnifyT t, JImGui imGui) {
    var s = t.lhs().toDoc().renderWithPageWidth(PAGE_WIDTH)
      + " = "
      + t.rhs().toDoc().renderWithPageWidth(PAGE_WIDTH);
    visitSub(s, Color.WHITE, imGui, t.subtraces(), () -> pos = t.pos());
    return Unit.unit();
  }

  @Override public Unit visitDecl(Trace.@NotNull DeclT t, JImGui imGui) {
    visitSub(t.var().name(), Color.WHITE, imGui, t.subtraces(), () -> pos = t.pos());
    return Unit.unit();
  }
}
