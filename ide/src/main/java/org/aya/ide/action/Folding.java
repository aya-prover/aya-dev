// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.action;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.cli.library.source.LibrarySource;
import org.aya.ide.Resolver;
import org.aya.ide.syntax.SyntaxDeclAction;
import org.aya.syntax.concrete.stmt.Command;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.concrete.stmt.decl.Decl;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public record Folding(@NotNull MutableList<FoldingArea> foldingRanges) implements SyntaxDeclAction {
  public static @NotNull ImmutableSeq<FoldingArea> invoke(@NotNull LibrarySource source) {
    var folder = new Folding(MutableList.create());
    var program = source.program().get();
    if (program != null) program.forEach(folder);
    return folder.foldingRanges.toImmutableSeq();
  }

  @Override public void accept(@NotNull Stmt stmt) {
    switch (stmt) {
      case Decl maybe -> Resolver.withChildren(maybe)
        .map(dv -> dv.concrete)
        .map(Decl::entireSourcePos)
        .map(pos -> new FoldingArea(pos, maybe))
        .forEach(foldingRanges::append);
      case Command.Module mod -> foldingRanges.append(new FoldingArea(mod.entireSourcePos(), stmt));
      default -> { }
    }
    SyntaxDeclAction.super.accept(stmt);
  }

  public record FoldingArea(@NotNull SourcePos entireSourcePos, @NotNull Stmt stmt) { }
}
