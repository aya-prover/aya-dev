// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.mzi.cli;

import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.tuple.Unit;
import org.ice1000.jimgui.*;
import org.ice1000.jimgui.flag.JImHoveredFlags;
import org.ice1000.jimgui.util.JImGuiUtil;
import org.ice1000.jimgui.util.JniLoader;
import org.jetbrains.annotations.NotNull;
import org.mzi.pretty.error.PrettyErrorConfig;
import org.mzi.pretty.error.Span;
import org.mzi.tyck.trace.Trace;

import java.awt.*;

@SuppressWarnings("AccessStaticViaInstance")
public class ImGuiTrace implements Trace.Visitor<Unit, Unit> {
  private JImGui imGui;
  private final String sourceCode;
  private @NotNull Span.Data spanData;
  private int inc = 0;

  public ImGuiTrace(@NotNull String sourceCode) {
    this.sourceCode = sourceCode;
    spanData = new Span.Data(-1, -1, -1, -1);
  }

  private @NotNull JImVec4 color(Color color) {
    return new MutableJImVec4(color.getRed() / 256f,
      color.getGreen() / 256f,
      color.getBlue() / 256f,
      color.getAlpha() / 256f);
  }

  public void mainLoop(@NotNull Seq<@NotNull Trace> root) {
    JniLoader.load();
    imGui = new JImGui();
    imGui.pushStyleVar(JImStyleVars.ItemSpacing, 0f, 2f);
    JImGuiUtil.cacheStringToBytes();
    var highlight = color(Color.GREEN);
    while (!imGui.windowShouldClose()) {
      imGui.initNewFrame();
      inc = 0;
      var line = 1;
      var column = 1;
      var buffer = new StringBuilder();
      var previousContains = false;
      for (var i = 0; i < sourceCode.length(); i++) {
        var c = sourceCode.charAt(i);
        buffer.append(c);
        var isEOL = c == '\n';
        var contains = spanData.contains(line, column);
        if (contains != previousContains) {
          if (!contains) imGui.textColored(highlight, buffer.toString());
          else imGui.text(buffer.toString());
          buffer.delete(0, buffer.length());
        }
        previousContains = contains;
        if (isEOL) {
          line++;
          column = 1;
          imGui.text(buffer.toString());
          buffer.delete(0, buffer.length());
          imGui.newLine();
        } else {
          column++;
          imGui.sameLine();
        }
      }
      root.forEach(e -> e.accept(this, Unit.unit()));
      imGui.render();
    }
    highlight.deallocateNativeObject();
    imGui.deallocateNativeObject();
  }

  @Override public Unit visitExpr(Trace.@NotNull ExprT t, Unit unit) {
    var s = t.expr().toDoc().renderWithPageWidth(114514) + "##" + inc++;
    var color = t.term() == null ? Color.CYAN : Color.YELLOW;
    visitSub(s, color, t.subtraces(), () ->
    {
      spanData = t.expr().sourcePos().toSpan(sourceCode).normalize(PrettyErrorConfig.DEFAULT);
      System.out.println(spanData);
    });
    return unit;
  }

  private void visitSub(String s, Color color, Buffer<@NotNull Trace> subtraces, @NotNull Runnable callback) {
    final var vec4 = color(color);
    imGui.pushStyleColor(JImStyleColors.Text, vec4);
    if (imGui.treeNode(s)) {
      if (imGui.isItemHovered(JImHoveredFlags.AllowWhenBlockedByActiveItem)) callback.run();
      subtraces.forEach(e -> e.accept(this, Unit.unit()));
      imGui.treePop();
    }
    imGui.popStyleColor();
    vec4.deallocateNativeObject();
  }

  @Override public Unit visitUnify(Trace.@NotNull UnifyT t, Unit unit) {
    visitSub("conversion check", Color.WHITE, t.subtraces(), () -> {});
    return unit;
  }
}
