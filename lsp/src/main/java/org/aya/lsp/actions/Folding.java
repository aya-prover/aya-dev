// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import kala.collection.mutable.MutableList;
import org.aya.cli.library.source.LibrarySource;
import org.aya.concrete.stmt.Command;
import org.aya.concrete.stmt.Decl;
import org.aya.lsp.utils.LspRange;
import org.aya.lsp.utils.Resolver;
import org.aya.util.error.SourcePos;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class Folding implements SyntaxDeclAction<@NotNull MutableList<FoldingRange>> {
  public static @NotNull List<FoldingRange> invoke(@NotNull LibrarySource source) {
    var ranges = MutableList.<FoldingRange>create();
    var folder = new Folding();
    var program = source.program().value;
    if (program != null) program.forEach(decl -> folder.visit(decl, ranges));
    return ranges.asJava();
  }

  @Override
  public void visitCommand(@NotNull Command cmd, @NotNull MutableList<FoldingRange> pp) {
    if (cmd instanceof Command.Module mod) pp.append(toFoldingRange(mod.entireSourcePos()));
    SyntaxDeclAction.super.visitCommand(cmd, pp);
  }

  @Override public void visitDecl(@NotNull Decl maybe, @NotNull MutableList<FoldingRange> pp) {
    Resolver.withChildren(maybe).filter(dv -> dv.concrete != null)
      .map(dv -> dv.concrete.entireSourcePos())
      .filter(pos -> pos.linesOfCode() >= 3)
      .forEach(pos -> pp.append(toFoldingRange(pos)));
    SyntaxDeclAction.super.visitDecl(maybe, pp);
  }

  private @NotNull FoldingRange toFoldingRange(@NotNull SourcePos sourcePos) {
    var range = LspRange.toRange(sourcePos);
    var fr = new FoldingRange(range.getStart().getLine(), range.getEnd().getLine());
    fr.setStartCharacter(range.getStart().getCharacter());
    fr.setEndCharacter(range.getEnd().getCharacter());
    fr.setKind(FoldingRangeKind.Region);
    return fr;
  }
}
