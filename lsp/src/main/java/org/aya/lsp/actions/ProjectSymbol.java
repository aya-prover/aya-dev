// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.concrete.stmt.Decl;
import org.aya.lsp.utils.LspRange;
import org.aya.lsp.utils.Resolver;
import org.aya.ref.DefVar;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;

public final class ProjectSymbol implements SyntaxDeclAction<@NotNull MutableList<ProjectSymbol.Symbol>> {
  private static final @NotNull ProjectSymbol INSTANCE = new ProjectSymbol();

  public static @NotNull ImmutableSeq<Symbol> invoke(@NotNull LibrarySource source) {
    var symbols = MutableList.<Symbol>create();
    collect(source, symbols);
    return symbols.toImmutableSeq();
  }

  public static @NotNull ImmutableSeq<Symbol> invoke(@NotNull SeqView<LibraryOwner> libraries) {
    var symbols = MutableList.<Symbol>create();
    libraries.forEach(lib -> collect(lib, symbols));
    return symbols.toImmutableSeq();
  }

  private static void collect(@NotNull LibraryOwner owner, @NotNull MutableList<Symbol> symbols) {
    owner.librarySources().forEach(src -> collect(src, symbols));
    owner.libraryDeps().forEach(lib -> collect(lib, symbols));
  }

  private static void collect(@NotNull LibrarySource src, @NotNull MutableList<Symbol> symbols) {
    var program = src.program().get();
    if (program != null) program.forEach(decl -> INSTANCE.visit(decl, symbols));
  }

  @Override public void visitDecl(@NotNull Decl decl, @NotNull MutableList<Symbol> pp) {
    var children = MutableList.<Symbol>create();
    Resolver.withChildren(decl)
      .filter(dv -> dv.concrete != decl && dv.concrete != null)
      .forEach(dv -> collect(children, dv, ImmutableSeq.empty()));
    collect(pp, decl.ref(), children.toImmutableSeq());
    SyntaxDeclAction.super.visitDecl(decl, pp);
  }

  private void collect(@NotNull MutableList<Symbol> pp, @NotNull DefVar<?, ?> dv, @NotNull ImmutableSeq<Symbol> children) {
    var nameLoc = LspRange.toLoc(dv.concrete.sourcePos());
    var entireLoc = LspRange.toLoc(dv.concrete.entireSourcePos());
    if (nameLoc == null || entireLoc == null) return;
    var symbol = new Symbol(
      dv.name(),
      ComputeSignature.computeSignature(dv, true).commonRender(),
      SymbolKind.Function, // TODO: refactor kindOf from SyntaxHighlight
      nameLoc, entireLoc, children);
    pp.append(symbol);
  }

  /** Our superclass of {@link org.eclipse.lsp4j.WorkspaceSymbol} and {@link org.eclipse.lsp4j.DocumentSymbol} */
  public record Symbol(
    @NotNull String name,
    @NotNull String description,
    @NotNull SymbolKind kind,
    @NotNull Location nameLocation,
    @NotNull Location entireLocation,
    @NotNull ImmutableSeq<Symbol> children
  ) {
    public @NotNull DocumentSymbol document() {
      return new DocumentSymbol(name, kind, entireLocation.getRange(), nameLocation.getRange(),
        description, children.map(Symbol::document).asJava());
    }

    public @NotNull WorkspaceSymbol workspace() {
      return new WorkspaceSymbol(name, kind, Either.forLeft(nameLocation));
    }
  }
}
