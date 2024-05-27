// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.cli.literate.HighlightInfo;
import org.aya.ide.action.ProjectSymbol;
import org.aya.ide.action.ProjectSymbol.Symbol;
import org.aya.lsp.utils.LspRange;
import org.aya.util.prettier.PrettierOptions;
import org.intellij.lang.annotations.MagicConstant;
import org.javacs.lsp.DocumentSymbol;
import org.javacs.lsp.SymbolKind;
import org.javacs.lsp.WorkspaceSymbol;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface SymbolMaker {
  static @NotNull ImmutableSeq<DocumentSymbol> documentSymbols(@NotNull PrettierOptions options, @NotNull LibrarySource source) {
    return ProjectSymbol.invoke(options, source).map(SymbolMaker::documentSymbol);
  }

  static @NotNull ImmutableSeq<WorkspaceSymbol> workspaceSymbols(@NotNull PrettierOptions options, @NotNull SeqView<LibraryOwner> libraries) {
    return ProjectSymbol.invoke(options, libraries).mapNotNull(SymbolMaker::workspaceSymbol);
  }

  private static int kindOf(@NotNull HighlightInfo.DefKind kind) {
    // https://youtrack.jetbrains.com/issue/IDEA-308218/
    @MagicConstant(valuesFromClass = SymbolKind.class)
    int symbolKind = switch (kind) {
      case Data -> SymbolKind.Enum;
      case Con -> SymbolKind.EnumMember;
      case Clazz -> SymbolKind.Struct;
      case Member -> SymbolKind.Field;
      case Fn, Prim -> SymbolKind.Function;
      case Generalized -> SymbolKind.TypeParameter;
      case LocalVar -> SymbolKind.Variable;
      case Module -> SymbolKind.Module;
      case Unknown -> SymbolKind.Null;
    };
    return symbolKind;
  }

  private static @Nullable WorkspaceSymbol workspaceSymbol(@NotNull Symbol symbol) {
    var nameLoc = LspRange.toLoc(symbol.nameLocation());
    if (nameLoc == null) return null;
    return new WorkspaceSymbol(symbol.name(), kindOf(symbol.kind()), nameLoc);
  }

  private static @NotNull DocumentSymbol documentSymbol(@NotNull Symbol symbol) {
    var nameLoc = LspRange.toRange(symbol.nameLocation());
    var entireLoc = LspRange.toRange(symbol.entireLocation());
    return new DocumentSymbol(
      symbol.name(), symbol.description().commonRender(), kindOf(symbol.kind()),
      false, entireLoc, nameLoc,
      symbol.children().map(SymbolMaker::documentSymbol).asJava());
  }
}
