// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.action;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.cli.literate.HighlightInfo;
import org.aya.cli.literate.SyntaxHighlight;
import org.aya.ide.Resolver;
import org.aya.ide.syntax.SyntaxDeclAction;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.concrete.stmt.decl.Decl;
import org.aya.syntax.ref.DefVar;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record ProjectSymbol(
  @NotNull PrettierOptions options,
  @NotNull MutableList<Symbol> symbols
) implements SyntaxDeclAction {
  public static @NotNull ImmutableSeq<Symbol> invoke(@NotNull PrettierOptions options, @NotNull LibrarySource source) {
    var symbol = new ProjectSymbol(options, MutableList.create());
    symbol.collectSource(source);
    return symbol.symbols.toImmutableSeq();
  }

  public static @NotNull ImmutableSeq<Symbol> invoke(@NotNull PrettierOptions options, @NotNull SeqView<LibraryOwner> libraries) {
    var symbol = new ProjectSymbol(options, MutableList.create());
    libraries.forEach(symbol::collectLib);
    return symbol.symbols.toImmutableSeq();
  }

  private void collectLib(@NotNull LibraryOwner owner) {
    owner.librarySources().forEach(this::collectSource);
    owner.libraryDeps().forEach(this::collectLib);
  }

  private void collectSource(@NotNull LibrarySource src) {
    var program = src.program().get();
    if (program != null) program.forEach(this);
  }

  @Override public void accept(@NotNull Stmt stmt) {
    if (stmt instanceof Decl decl) {
      var children = new ProjectSymbol(options, MutableList.create());
      Resolver.withChildren(decl)
        .filter(dv -> dv.concrete != decl)
        .forEach(dv -> children.collect(dv, null));
      collect(decl.ref(), children);
    }
    SyntaxDeclAction.super.accept(stmt);
  }

  private void collect(@NotNull DefVar<?, ?> dv, @Nullable ProjectSymbol children) {
    var nameLoc = dv.concrete.sourcePos();
    var entireLoc = dv.concrete.entireSourcePos();
    var symbol = new Symbol(
      dv.name(),
      ComputeSignature.computeSignature(options, dv),
      SyntaxHighlight.kindOf(dv),
      nameLoc, entireLoc,
      children == null ? ImmutableSeq.empty() : children.symbols.toImmutableSeq());
    symbols.append(symbol);
  }

  public record Symbol(
    @NotNull String name,
    @NotNull Doc description,
    @NotNull HighlightInfo.DefKind kind,
    @NotNull SourcePos nameLocation,
    @NotNull SourcePos entireLocation,
    @NotNull ImmutableSeq<Symbol> children
  ) {}
}
