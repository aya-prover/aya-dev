// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.hatate;

import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.concrete.desugar.BinOpSet;
import org.aya.concrete.resolve.context.Context;
import org.aya.concrete.stmt.Stmt;
import org.commonmark.parser.Parser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000
 */
public final class Remark implements Stmt {
  public final @Nullable Literate literate;
  public final @NotNull String raw;
  public final @NotNull SourcePos sourcePos;
  public @Nullable Context ctx = null;

  private Remark(@Nullable Literate literate, @NotNull String raw, @NotNull SourcePos sourcePos) {
    this.literate = literate;
    this.raw = raw;
    this.sourcePos = sourcePos;
  }

  public static @NotNull Remark make(@NotNull String raw, @NotNull SourcePos pos, @NotNull Reporter reporter) {
    var parser = Parser.builder().build();
    var ast = parser.parse(raw);
    throw new UnsupportedOperationException();
  }

  @Override public @NotNull Accessibility accessibility() {
    return Accessibility.Private;
  }

  @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitRemark(this, p);
  }

  public @NotNull SourcePos sourcePos() {
    return sourcePos;
  }

  public void doResolve(@NotNull BinOpSet binOpSet) {
    if (literate == null) return;
    assert ctx != null : "Be sure to call the shallow resolver before resolving";
    literate.resolve(binOpSet, ctx);
  }
}
