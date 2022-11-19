// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.control.Option;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.stmt.*;
import org.aya.concrete.visitor.StmtOps;
import org.aya.core.def.*;
import org.aya.lsp.models.HighlightResult;
import org.aya.lsp.utils.LspRange;
import org.aya.ref.DefVar;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SyntaxHighlight implements StmtOps<@NotNull MutableList<HighlightResult.Symbol>> {
  public static @NotNull ImmutableSeq<HighlightResult> invoke(@NotNull LibraryOwner owner) {
    var symbols = MutableList.<HighlightResult>create();
    highlight(owner, symbols);
    return symbols.toImmutableSeq();
  }

  private static void highlight(@NotNull LibraryOwner owner, @NotNull MutableList<HighlightResult> result) {
    owner.librarySources().forEach(src -> result.append(highlightOne(src)));
    owner.libraryDeps().forEach(dep -> highlight(dep, result));
  }

  private static @NotNull HighlightResult highlightOne(@NotNull LibrarySource source) {
    var symbols = MutableList.<HighlightResult.Symbol>create();
    var program = source.program().get();
    if (program != null) program.forEach(d -> SyntaxHighlight.INSTANCE.visit(d, symbols));
    return new HighlightResult(
      source.file().toUri(),
      symbols.filter(t -> t.range() != LspRange.NONE));
  }

  private static final SyntaxHighlight INSTANCE = new SyntaxHighlight();

  // region def, data, struct, prim, levels
  @Override
  public void visitTelescopic(@NotNull Decl decl, Decl.@NotNull Telescopic proof, @NotNull MutableList<HighlightResult.Symbol> buffer) {
    buffer.append(new HighlightResult.Symbol(decl.sourcePos(), switch (proof) {
      case TeleDecl.DataDecl $ -> HighlightResult.Kind.DataDef;
      case TeleDecl.StructField $ -> HighlightResult.Kind.FieldDef;
      case TeleDecl.PrimDecl $ -> HighlightResult.Kind.PrimDef;
      case TeleDecl.DataCtor $ -> HighlightResult.Kind.ConDef;
      case TeleDecl.FnDecl $ -> HighlightResult.Kind.FnDef;
      case TeleDecl.StructDecl $ -> HighlightResult.Kind.StructDef;
    }));
    StmtOps.super.visitTelescopic(decl, proof, buffer);
  }

  @Override public void visitDecl(@NotNull Decl decl, @NotNull MutableList<HighlightResult.Symbol> buffer) {
    visitBind(buffer, decl.bindBlock());
    StmtOps.super.visitDecl(decl, buffer);
  }

  @Override public void visit(@NotNull Stmt stmt, @NotNull MutableList<HighlightResult.Symbol> pp) {
    if (stmt instanceof Generalize generalize) {
      for (var generalized : generalize.variables)
        pp.append(new HighlightResult.Symbol(generalized.sourcePos,
          HighlightResult.Kind.Generalize));
    }
    StmtOps.super.visit(stmt, pp);
  }
  // endregion

  // region call terms
  @Override public @NotNull Expr visitExpr(@NotNull Expr expr, @NotNull MutableList<HighlightResult.Symbol> pp) {
    switch (expr) {
      case Expr.Ref ref -> {
        if (ref.resolvedVar() instanceof DefVar<?, ?> defVar) visitCall(defVar, ref.sourcePos(), pp);
      }
      case Expr.Proj proj -> {
        if (proj.ix().isRight() && proj.resolvedVar() instanceof DefVar<?, ?> defVar)
          visitCall(defVar, proj.ix().getRightValue().sourcePos(), pp);
      }
      case Expr.Coe coe -> visitCall(coe.resolvedVar(), coe.id().sourcePos(), pp);
      case Expr.New neo -> neo.fields().forEach(field -> {
        if (field.resolvedField().get() instanceof DefVar<?, ?> defVar)
          visitCall(defVar, field.name().sourcePos(), pp);
      });
      default -> {}
    }
    return StmtOps.super.visitExpr(expr, pp);
  }

  private void visitCall(@NotNull DefVar<?, ?> ref, @NotNull SourcePos headPos, @NotNull MutableList<HighlightResult.Symbol> buffer) {
    var kind = kindOf(ref);
    if (kind != null) buffer.append(new HighlightResult.Symbol(headPos, kind));
  }
  // endregion

  // region pattern
  @Override
  public @NotNull Pattern visitPattern(@NotNull Pattern pattern, @NotNull MutableList<HighlightResult.Symbol> symbols) {
    if (pattern instanceof Pattern.Ctor ctor) {
      if (ctor.resolved().data() instanceof DefVar<?, ?> defVar)
        visitCall(defVar, ctor.resolved().sourcePos(), symbols);
    }
    return StmtOps.super.visitPattern(pattern, symbols);
  }
  // endregion

  // region import, open, module

  @Override public void visitCommand(@NotNull Command cmd, @NotNull MutableList<HighlightResult.Symbol> pp) {
    switch (cmd) {
      case Command.Import imp ->
        pp.append(new HighlightResult.Symbol(imp.path().sourcePos(), HighlightResult.Kind.ModuleDef));
      case Command.Open open ->
        pp.append(new HighlightResult.Symbol(open.path().sourcePos(), HighlightResult.Kind.ModuleDef));
      case Command.Module mod -> pp.append(new HighlightResult.Symbol(mod.sourcePos(), HighlightResult.Kind.ModuleDef));
    }
    StmtOps.super.visitCommand(cmd, pp);
  }

  private HighlightResult.@Nullable Kind kindOf(@NotNull DefVar<?, ?> ref) {
    if (ref.core instanceof FnDef || ref.concrete instanceof TeleDecl.FnDecl)
      return HighlightResult.Kind.FnCall;
    else if (ref.core instanceof StructDef || ref.concrete instanceof TeleDecl.StructDecl)
      return HighlightResult.Kind.StructCall;
    else if (ref.core instanceof FieldDef || ref.concrete instanceof TeleDecl.StructField)
      return HighlightResult.Kind.FieldCall;
    else if (ref.core instanceof PrimDef || ref.concrete instanceof TeleDecl.PrimDecl)
      return HighlightResult.Kind.PrimCall;
    else if (ref.core instanceof DataDef || ref.concrete instanceof TeleDecl.DataDecl)
      return HighlightResult.Kind.DataCall;
    else if (ref.core instanceof CtorDef || ref.concrete instanceof TeleDecl.DataCtor)
      return HighlightResult.Kind.ConCall;
    return null;
  }

  private void visitOperator(@NotNull MutableList<HighlightResult.Symbol> buffer, @NotNull SourcePos sourcePos, @Nullable DefVar<?, ?> op) {
    Option.ofNullable(op).filter(DefVar::isInfix).mapNotNull(this::kindOf)
      .forEach(kind -> buffer.append(new HighlightResult.Symbol(sourcePos, kind)));
  }

  private void visitBind(@NotNull MutableList<HighlightResult.Symbol> buffer, @NotNull BindBlock bindBlock) {
    if (bindBlock == BindBlock.EMPTY) return;
    var loosers = bindBlock.resolvedLoosers().get();
    var tighters = bindBlock.resolvedTighters().get();
    if (loosers != null) bindBlock.loosers().view().zip(loosers.view())
      .forEach(tup -> visitOperator(buffer, tup._1.sourcePos(), tup._2));
    if (tighters != null) bindBlock.tighters().view().zip(tighters.view())
      .forEach(tup -> visitOperator(buffer, tup._1.sourcePos(), tup._2));
  }
  // endregion
}
