// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.trace;

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

  @Override public Unit visitDecl(Trace.@NotNull DeclT t, Unit unit) {
    indent();
    builder.append("+ ").append(t.var().name());
    visitSub(t.subtraces());
    return unit;
  }

  @Override public Unit visitExpr(Trace.@NotNull ExprT t, Unit unit) {
    indent();
    builder.append("+ \u22A2 `")
      .append(t.expr().toDoc().renderWithPageWidth(114514))
      .append("`");
    if (t.term() != null) builder.append(" : ")
      .append(t.term().toDoc().renderWithPageWidth(114514));
    visitSub(t.subtraces());
    return unit;
  }

  private void indent() {
    builder.append(" ".repeat(indent));
  }

  private void visitSub(@NotNull Buffer<@NotNull Trace> subtraces) {
    builder.append(lineSep);
    if (subtraces.isEmpty()) return;
    indent += indentation;
    for (var subtrace : subtraces) subtrace.accept(this, Unit.unit());
    indent -= indentation;
  }

  @Override public Unit visitUnify(Trace.@NotNull UnifyT t, Unit unit) {
    indent();
    builder.append("+ \u22A2 ")
      .append(t.lhs().toDoc().renderWithPageWidth(114514))
      .append(" \u2261 ")
      .append(t.rhs().toDoc().renderWithPageWidth(114514));
    visitSub(t.subtraces());
    return unit;
  }

  @Override
  public Unit visitTyck(Trace.@NotNull TyckT t, Unit unit) {
    indent();
    builder.append("result \u22A2 `")
      .append(t.term().toDoc().renderWithPageWidth(114514))
      .append("` \u2191 ")
      .append(t.type().toDoc().renderWithPageWidth(114514))
      .append(lineSep);
    return unit;
  }
}
