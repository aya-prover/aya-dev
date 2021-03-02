// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.mzi.cli;

import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.tuple.Unit;
import org.ice1000.jimgui.*;
import org.ice1000.jimgui.util.JImGuiUtil;
import org.jetbrains.annotations.NotNull;
import org.mzi.tyck.trace.Trace;

import java.awt.*;

@SuppressWarnings("AccessStaticViaInstance")
public record ImGuiTrace(@NotNull JImGui imGui) implements Trace.Visitor<Unit, Unit> {
  private @NotNull JImVec4 color(Color color) {
    return new MutableJImVec4(color.getRed() / 256f,
      color.getGreen() / 256f,
      color.getBlue() / 256f,
      color.getAlpha() / 256f);
  }

  public void mainLoop(@NotNull Seq<@NotNull Trace> root) {
    imGui.pushStyleVar(JImStyleVars.ItemSpacing, 0f, 2f);
    JImGuiUtil.cacheStringToBytes();
    while (!imGui.windowShouldClose()) {
      imGui.initNewFrame();
      root.forEach(e -> e.accept(this, Unit.unit()));
      imGui.render();
    }
  }

  @Override public Unit visitExpr(Trace.@NotNull ExprT t, Unit unit) {
    var s = t.expr().toDoc().renderWithPageWidth(114514);
    var color = t.term() == null ? Color.CYAN : Color.YELLOW;
    visitSub(s, color, t.subtraces());
    return unit;
  }

  private void visitSub(String s, Color color, Buffer<@NotNull Trace> subtraces) {
    final var vec4 = color(color);
    imGui.pushStyleColor(JImStyleColors.Text, vec4);
    if (imGui.treeNode(s)) {
      subtraces.forEach(e -> e.accept(this, Unit.unit()));
      imGui.treePop();
    }
    imGui.popStyleColor();
    vec4.deallocateNativeObject();
  }

  @Override public Unit visitUnify(Trace.@NotNull UnifyT t, Unit unit) {
    visitSub("conversion check", Color.WHITE, t.subtraces());
    return unit;
  }
}
