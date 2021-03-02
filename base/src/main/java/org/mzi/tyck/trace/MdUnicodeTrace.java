// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.mzi.tyck.trace;

import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

public class MdUnicodeTrace implements Trace.Visitor<Unit, Unit> {
  public final @NotNull StringBuilder builder;
  private int indent = 0;
  private int indentation;

  public MdUnicodeTrace(int capacity, int indentation) {
    this.builder = new StringBuilder(capacity);
    this.indentation = indentation;
  }

  public MdUnicodeTrace() {
    this(2048, 2);
  }

  private void eol() {
    builder.append(System.lineSeparator()).append(" ".repeat(Math.max(0, indent)));
  }

  @Override public Unit visitExpr(Trace.@NotNull ExprT t, Unit unit) {
    builder.append("+ \u22A2 ")
      .append(t.expr().toDoc().renderWithPageWidth(114514))
      .append(" : ")
      .append("expected type");
    visitSub(t.subtraces());
    return unit;
  }

  private void visitSub(@NotNull Buffer<@NotNull Trace> subtraces) {
    indent += indentation;
    builder.append(System.lineSeparator()).append(" ".repeat(Math.max(0, indent)));
    for (var subtrace : subtraces) subtrace.accept(this, Unit.unit());
    indent -= indentation;
  }

  @Override public Unit visitUnify(Trace.@NotNull UnifyT t, Unit unit) {
    builder.append("+ \u22A2 a = b");
    visitSub(t.subtraces());
    return unit;
  }
}
