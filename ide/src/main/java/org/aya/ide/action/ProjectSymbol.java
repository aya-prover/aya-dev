// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.action;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.cli.literate.HighlightInfo;
import org.aya.cli.literate.SyntaxHighlight;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Stmt;
import org.aya.ide.Resolver;
import org.aya.ide.syntax.SyntaxDeclAction;
import org.aya.ref.DefVar;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record ProjectSymbol(@NotNull MutableList<Symbol> symbols) implements SyntaxDeclAction {
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
    var nameLoc = dv.concrete.sourcePos();
    var entireLoc = dv.concrete.entireSourcePos();
    var symbol = new Symbol(
      dv.name(),
      ComputeSignature.computeSignature(dv, true).commonRender(),
      SyntaxHighlight.kindOf(dv),
      nameLoc, entireLoc,
      children == null ? ImmutableSeq.empty() : children.symbols.toImmutableSeq());
    ps.symbols.append(symbol);
  }

  public record Symbol(
    @NotNull String name,
    @NotNull String description,
    @NotNull HighlightInfo.DefKind kind,
    @NotNull SourcePos nameLocation,
    @NotNull SourcePos entireLocation,
    @NotNull ImmutableSeq<Symbol> children
  ) {}
}
