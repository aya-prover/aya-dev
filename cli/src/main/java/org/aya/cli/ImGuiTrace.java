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

import java.util.Objects;

@SuppressWarnings("AccessStaticViaInstance")
public class ImGuiTrace implements Trace.Visitor<JImGui, Unit> {
  private static record Color(int red, int green, int blue, @NotNull MutableJImVec4 vec4) {
    public Color(int red, int green, int blue) {
      this(red, green, blue,
        new MutableJImVec4(red / 256f, green / 256f, blue / 256f, 1));
    }

    public static Color GREEN = new Color(0, 255, 0);
    public static Color CYAN = new Color(0, 255, 255);
    public static Color YELLOW = new Color(255, 255, 0);
    public static Color WHITE = new Color(255, 255, 255);
  }

  public static final int PAGE_WIDTH = 114514;
  private final String sourceCode;
  private @NotNull SourcePos pos;

  public ImGuiTrace(@NotNull String sourceCode) {
    this.sourceCode = sourceCode;
    pos = SourcePos.NONE;
  }

  public void mainLoop(@NotNull Seq<@NotNull Trace> root) {
    JniLoader.load();
    var imGui = new JImGui();
    imGui.pushStyleVar(JImStyleVars.ItemSpacing, 0f, 1.5f);
    imGui.getStyle().scaleAllSizes(1.5f);
    try (var config = new JImFontConfig()) {
      var fontAtlas = imGui.getIO().getFonts();
      fontAtlas.clearFonts();
      config.setSizePixels(26);
      fontAtlas.addDefaultFont(config);
    }
    JImGuiUtil.cacheStringToBytes();
    var highlight = Color.GREEN.vec4;
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
      + (term == null ? "" : " : " + term.toDoc().renderWithPageWidth(PAGE_WIDTH));
    var color = term == null ? Color.CYAN : Color.YELLOW;
    visitSub(s, color, imGui, t.subtraces(), () -> pos = t.expr().sourcePos(), Objects.hashCode(t));
    return Unit.unit();
  }

  private void visitSub(
    String s, Color color, JImGui imGui,
    Buffer<@NotNull Trace> subtraces,
    @NotNull Runnable callback,
    int hashCode
  ) {
    imGui.pushStyleColor(JImStyleColors.Text, color.vec4);
    var node = false;
    if (subtraces.isEmpty()) imGui.bulletText(s);
    else node = imGui.treeNode(s + "##" + hashCode);
    if (imGui.isItemHovered()) callback.run();
    if (node) {
      subtraces.forEach(e -> e.accept(this, imGui));
      imGui.treePop();
    }
    imGui.popStyleColor();
  }

  @Override public Unit visitUnify(Trace.@NotNull UnifyT t, JImGui imGui) {
    var s = t.lhs().toDoc().renderWithPageWidth(PAGE_WIDTH)
      + " = "
      + t.rhs().toDoc().renderWithPageWidth(PAGE_WIDTH);
    visitSub(s, Color.WHITE, imGui, t.subtraces(), () -> pos = t.pos(), Objects.hashCode(t));
    return Unit.unit();
  }

  @Override public Unit visitDecl(Trace.@NotNull DeclT t, JImGui imGui) {
    visitSub(t.var().name(), Color.WHITE, imGui, t.subtraces(), () -> pos = t.pos(), Objects.hashCode(t));
    return Unit.unit();
  }

  @Override
  public Unit visitTyck(Trace.@NotNull TyckT t, JImGui imGui) {
    var term = t.term();
    var type = t.type();
    var s = term.toDoc().renderWithPageWidth(PAGE_WIDTH) +
      " : " +
      type.toDoc().renderWithPageWidth(PAGE_WIDTH);
    imGui.text("-".repeat(s.length() + 4));
    visitSub(s, Color.YELLOW, imGui, Buffer.of(), () -> pos = t.pos(), Objects.hashCode(t));
    return Unit.unit();
  }

}
