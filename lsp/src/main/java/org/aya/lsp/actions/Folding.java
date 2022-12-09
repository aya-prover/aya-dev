// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import kala.collection.mutable.MutableList;
import org.aya.cli.library.source.LibrarySource;
import org.aya.concrete.stmt.Command;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Stmt;
import org.aya.ide.syntax.SyntaxDeclAction;
import org.aya.lsp.utils.LspRange;
import org.aya.ide.Resolver;
import org.aya.util.error.SourcePos;
import org.javacs.lsp.FoldingRange;
import org.javacs.lsp.FoldingRangeKind;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record Folding(@NotNull MutableList<FoldingRange> foldingRanges) implements SyntaxDeclAction {
  public static @NotNull List<FoldingRange> invoke(@NotNull LibrarySource source) {
    var folder = new Folding(MutableList.create());
    var program = source.program().get();
    if (program != null) program.forEach(folder);
    return folder.foldingRanges.asJava();
  }

  @Override public void accept(@NotNull Stmt stmt) {
    switch (stmt) {
      case Decl maybe -> Resolver.withChildren(maybe).filter(dv -> dv.concrete != null)
        .map(dv -> dv.concrete.entireSourcePos())
        .filter(pos -> pos.linesOfCode() >= 3)
        .forEach(pos -> foldingRanges.append(toFoldingRange(pos)));
      case Command.Module mod -> foldingRanges.append(toFoldingRange(mod.entireSourcePos()));
      default -> {}
    }
    SyntaxDeclAction.super.accept(stmt);
  }

  private @NotNull FoldingRange toFoldingRange(@NotNull SourcePos sourcePos) {
    var range = LspRange.toRange(sourcePos);
    return new FoldingRange(range.start.line, range.start.character,
      range.end.line, range.end.character, FoldingRangeKind.Region);
  }
}
