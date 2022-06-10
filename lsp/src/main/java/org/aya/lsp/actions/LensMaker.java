// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import kala.collection.SeqView;
import kala.collection.mutable.MutableList;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.visitor.StmtOps;
import org.aya.lsp.utils.LspRange;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record LensMaker(@NotNull SeqView<LibraryOwner> libraries) implements StmtOps<@NotNull MutableList<CodeLens>> {
  public static @NotNull List<CodeLens> invoke(@NotNull LibrarySource source, @NotNull SeqView<LibraryOwner> libraries) {
    var lens = MutableList.<CodeLens>create();
    var maker = new LensMaker(libraries);
    var program = source.program().value;
    if (program != null) program.forEach(decl -> maker.visit(decl, lens));
    return lens.asJava();
  }

  @Override
  public void visitDecl(@NotNull Decl decl, @NotNull MutableList<CodeLens> pp) {
    var refs = FindReferences.findRefs(SeqView.of(decl.ref()), libraries).size();
    if (refs > 0) {
      pp.append(new CodeLens(LspRange.toRange(decl),
        new Command("%d %s".formatted(refs, refs > 1 ? "usages" : "usage"), ""),
        null));
    }
    StmtOps.super.visitDecl(decl, pp);
  }
}
