// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Stmt;
import org.aya.lsp.utils.LspRange;
import org.aya.lsp.utils.Resolver;
import org.aya.ref.DefVar;
import org.intellij.lang.annotations.MagicConstant;
import org.javacs.lsp.DocumentSymbol;
import org.javacs.lsp.Location;
import org.javacs.lsp.SymbolKind;
import org.javacs.lsp.WorkspaceSymbol;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record ProjectSymbol(@NotNull MutableList<ProjectSymbol.Symbol> symbols) implements SyntaxDeclAction {
  public static @NotNull ImmutableSeq<Symbol> invoke(@NotNull LibrarySource source) {
    var symbol = new ProjectSymbol(MutableList.create());
    collect(source, symbol);
    return symbol.symbols.toImmutableSeq();
  }

  public static @NotNull ImmutableSeq<Symbol> invoke(@NotNull SeqView<LibraryOwner> libraries) {
    var symbol = new ProjectSymbol(MutableList.create());
    libraries.forEach(lib -> collect(lib, symbol));
    return symbol.symbols.toImmutableSeq();
  }

  private static void collect(@NotNull LibraryOwner owner, @NotNull ProjectSymbol symbol) {
    owner.librarySources().forEach(src -> collect(src, symbol));
    owner.libraryDeps().forEach(lib -> collect(lib, symbol));
  }

  private static void collect(@NotNull LibrarySource src, @NotNull ProjectSymbol symbol) {
    var program = src.program().get();
    if (program != null) program.forEach(symbol);
  }

  @Override public void accept(@NotNull Stmt stmt) {
    if (stmt instanceof Decl decl) {
      var children = new ProjectSymbol(MutableList.create());
      Resolver.withChildren(decl)
        .filter(dv -> dv.concrete != decl && dv.concrete != null)
        .forEach(dv -> collect(children, dv, null));
      collect(this, decl.ref(), children);
    }
    SyntaxDeclAction.super.accept(stmt);
  }

  private static void collect(@NotNull ProjectSymbol ps, @NotNull DefVar<?, ?> dv, @Nullable ProjectSymbol children) {
    var nameLoc = LspRange.toLoc(dv.concrete.sourcePos());
    var entireLoc = LspRange.toLoc(dv.concrete.entireSourcePos());
    if (nameLoc == null || entireLoc == null) return;
    var symbol = new Symbol(
      dv.name(),
      ComputeSignature.computeSignature(dv, true).commonRender(),
      SymbolKind.Function, // TODO: refactor kindOf from SyntaxHighlight
      nameLoc, entireLoc,
      children == null ? ImmutableSeq.empty() : children.symbols.toImmutableSeq());
    ps.symbols.append(symbol);
  }

  /** Our superclass of {@link WorkspaceSymbol} and {@link DocumentSymbol} */
  public record Symbol(
    @NotNull String name,
    @NotNull String description,
    @MagicConstant(valuesFromClass = SymbolKind.class) int kind,
    @NotNull Location nameLocation,
    @NotNull Location entireLocation,
    @NotNull ImmutableSeq<Symbol> children
  ) {
    public @NotNull DocumentSymbol document() {
      return new DocumentSymbol(name, description, kind, false, entireLocation.range,
        nameLocation.range, children.map(Symbol::document).asJava());
    }

    public @NotNull WorkspaceSymbol workspace() {
      return new WorkspaceSymbol(name, kind, nameLocation);
    }
  }
}
