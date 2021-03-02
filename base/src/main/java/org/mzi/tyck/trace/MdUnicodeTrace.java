// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.mzi.tyck.trace;

import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

public class MdUnicodeTrace implements Trace.Visitor<Unit, Unit> {
  public final @NotNull StringBuilder builder;
  private int indent = 0;
  private final int indentation;
  public @NotNull String lineSep = System.lineSeparator();

  public MdUnicodeTrace(int capacity, int indentation) {
    this.builder = new StringBuilder(capacity);
    this.indentation = indentation;
  }

  public MdUnicodeTrace() {
    this(2048, 2);
  }

  @Override public Unit visitExpr(Trace.@NotNull ExprT t, Unit unit) {
    builder.append(" ".repeat(indent));
    builder.append("+ \u22A2 `")
      .append(t.expr().toDoc().renderWithPageWidth(114514))
      .append("` : ")
      .append(t.term() == null
        ? "expected type"
        : t.term().toDoc().renderWithPageWidth(114514));
    visitSub(t.subtraces());
    return unit;
  }

  private void visitSub(@NotNull Buffer<@NotNull Trace> subtraces) {
    builder.append(lineSep);
    if (subtraces.isEmpty()) return;
    indent += indentation;
    for (var subtrace : subtraces) subtrace.accept(this, Unit.unit());
    indent -= indentation;
  }

  @Override public Unit visitUnify(Trace.@NotNull UnifyT t, Unit unit) {
    builder.append(" ".repeat(indent));
    builder.append("+ \u22A2 conversion check");
    visitSub(t.subtraces());
    return unit;
  }
}
