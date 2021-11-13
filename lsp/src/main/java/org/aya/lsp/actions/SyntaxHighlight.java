// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import kala.collection.mutable.DynamicSeq;
import kala.tuple.Unit;
import org.aya.util.error.SourcePos;
import org.aya.api.ref.DefVar;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.stmt.*;
import org.aya.concrete.visitor.StmtConsumer;
import org.aya.lsp.models.HighlightResult;
import org.aya.lsp.utils.LspRange;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SyntaxHighlight implements StmtConsumer<@NotNull DynamicSeq<HighlightResult.Symbol>> {
  public static final SyntaxHighlight INSTANCE = new SyntaxHighlight();

  private @NotNull Range rangeOf(@NotNull Signatured signatured) {
    return LspRange.toRange(signatured.sourcePos());
  }

  // region def, data, struct, prim, levels
  @Override public Unit visitData(@NotNull Decl.DataDecl decl, @NotNull DynamicSeq<HighlightResult.Symbol> buffer) {
    buffer.append(new HighlightResult.Symbol(rangeOf(decl), HighlightResult.Symbol.Kind.DataDef));
    visitBind(buffer, decl.bindBlock);
    return StmtConsumer.super.visitData(decl, buffer);
  }

  @Override public Unit visitCtor(@NotNull Decl.DataCtor ctor, @NotNull DynamicSeq<HighlightResult.Symbol> buffer) {
    buffer.append(new HighlightResult.Symbol(rangeOf(ctor), HighlightResult.Symbol.Kind.ConDef));
    return StmtConsumer.super.visitCtor(ctor, buffer);
  }

  @Override public Unit visitStruct(@NotNull Decl.StructDecl decl, @NotNull DynamicSeq<HighlightResult.Symbol> buffer) {
    buffer.append(new HighlightResult.Symbol(rangeOf(decl), HighlightResult.Symbol.Kind.StructDef));
    visitBind(buffer, decl.bindBlock);
    return StmtConsumer.super.visitStruct(decl, buffer);
  }

  @Override
  public Unit visitField(@NotNull Decl.StructField field, @NotNull DynamicSeq<HighlightResult.Symbol> buffer) {
    buffer.append(new HighlightResult.Symbol(rangeOf(field), HighlightResult.Symbol.Kind.FieldDef));
    return StmtConsumer.super.visitField(field, buffer);
  }

  @Override public Unit visitFn(@NotNull Decl.FnDecl decl, @NotNull DynamicSeq<HighlightResult.Symbol> buffer) {
    buffer.append(new HighlightResult.Symbol(rangeOf(decl), HighlightResult.Symbol.Kind.FnDef));
    visitBind(buffer, decl.bindBlock);
    return StmtConsumer.super.visitFn(decl, buffer);
  }

  @Override public Unit visitPrim(@NotNull Decl.PrimDecl decl, @NotNull DynamicSeq<HighlightResult.Symbol> buffer) {
    buffer.append(new HighlightResult.Symbol(rangeOf(decl), HighlightResult.Symbol.Kind.PrimDef));
    return StmtConsumer.super.visitPrim(decl, buffer);
  }

  @Override
  public Unit visitLevels(Generalize.@NotNull Levels levels, @NotNull DynamicSeq<HighlightResult.Symbol> buffer) {
    for (var level : levels.levels())
      buffer.append(new HighlightResult.Symbol(LspRange.toRange(level.sourcePos()), HighlightResult.Symbol.Kind.Generalize));
    return StmtConsumer.super.visitLevels(levels, buffer);
  }
  // endregion

  // region call terms
  @Override public Unit visitRef(@NotNull Expr.RefExpr expr, @NotNull DynamicSeq<HighlightResult.Symbol> buffer) {
    if (expr.resolvedVar() instanceof DefVar<?, ?> defVar)
      visitCall(defVar, expr.sourcePos(), buffer);
    return StmtConsumer.super.visitRef(expr, buffer);
  }

  @Override public Unit visitProj(@NotNull Expr.ProjExpr expr, @NotNull DynamicSeq<HighlightResult.Symbol> buffer) {
    if (expr.resolvedIx().value instanceof DefVar<?, ?> defVar)
      visitCall(defVar, expr.ix().getRightValue().sourcePos(), buffer);
    return StmtConsumer.super.visitProj(expr, buffer);
  }

  @Override public Unit visitError(Expr.@NotNull ErrorExpr error, @NotNull DynamicSeq<HighlightResult.Symbol> symbols) {
    return Unit.unit();
  }

  private void visitCall(@NotNull DefVar<?, ?> ref, @NotNull SourcePos headPos, @NotNull DynamicSeq<HighlightResult.Symbol> buffer) {
    if (ref.concrete instanceof Decl.FnDecl)
      buffer.append(new HighlightResult.Symbol(LspRange.toRange(headPos), HighlightResult.Symbol.Kind.FnCall));
    else if (ref.concrete instanceof Decl.PrimDecl)
      buffer.append(new HighlightResult.Symbol(LspRange.toRange(headPos), HighlightResult.Symbol.Kind.PrimCall));
    else if (ref.concrete instanceof Decl.DataDecl)
      buffer.append(new HighlightResult.Symbol(LspRange.toRange(headPos), HighlightResult.Symbol.Kind.DataCall));
    else if (ref.concrete instanceof Decl.DataCtor)
      buffer.append(new HighlightResult.Symbol(LspRange.toRange(headPos), HighlightResult.Symbol.Kind.ConCall));
    else if (ref.concrete instanceof Decl.StructDecl)
      buffer.append(new HighlightResult.Symbol(LspRange.toRange(headPos), HighlightResult.Symbol.Kind.StructCall));
    else if (ref.concrete instanceof Decl.StructField)
      buffer.append(new HighlightResult.Symbol(LspRange.toRange(headPos), HighlightResult.Symbol.Kind.FieldCall));
  }

  // endregion

  // region pattern
  @Override public Unit visitBind(@NotNull Pattern.Bind bind, @NotNull DynamicSeq<HighlightResult.Symbol> buffer) {
    return StmtConsumer.super.visitBind(bind, buffer);
  }

  @Override public Unit visitCtor(@NotNull Pattern.Ctor ctor, @NotNull DynamicSeq<HighlightResult.Symbol> buffer) {
    if (ctor.resolved().data() instanceof DefVar<?, ?> defVar)
      visitCall(defVar, ctor.resolved().sourcePos(), buffer);
    return StmtConsumer.super.visitCtor(ctor, buffer);
  }
  // endregion

  // region import, open, module

  @Override public Unit visitImport(Command.@NotNull Import cmd, @NotNull DynamicSeq<HighlightResult.Symbol> buffer) {
    buffer.append(new HighlightResult.Symbol(LspRange.toRange(cmd.sourcePos()), HighlightResult.Symbol.Kind.ModuleDef));
    return StmtConsumer.super.visitImport(cmd, buffer);
  }

  @Override public Unit visitOpen(Command.@NotNull Open cmd, @NotNull DynamicSeq<HighlightResult.Symbol> buffer) {
    buffer.append(new HighlightResult.Symbol(LspRange.toRange(cmd.sourcePos()), HighlightResult.Symbol.Kind.ModuleDef));
    return StmtConsumer.super.visitOpen(cmd, buffer);
  }

  @Override public Unit visitModule(Command.@NotNull Module mod, @NotNull DynamicSeq<HighlightResult.Symbol> buffer) {
    buffer.append(new HighlightResult.Symbol(LspRange.toRange(mod.sourcePos()), HighlightResult.Symbol.Kind.ModuleDef));
    return StmtConsumer.super.visitModule(mod, buffer);
  }

  private HighlightResult.Symbol.@NotNull Kind kindOf(@NotNull Object opDecl) {
    return switch (opDecl) {
      case Decl.FnDecl ignored -> HighlightResult.Symbol.Kind.FnCall;
      case Decl.StructDecl ignored -> HighlightResult.Symbol.Kind.StructCall;
      case Decl.PrimDecl ignored -> HighlightResult.Symbol.Kind.PrimCall;
      case Decl.DataCtor ignored -> HighlightResult.Symbol.Kind.ConCall;
      default -> throw new IllegalArgumentException("Unsupported operator: " + opDecl.getClass().getName());
    };
  }

  private void visitOperator(@NotNull DynamicSeq<HighlightResult.Symbol> buffer, @NotNull SourcePos sourcePos, @Nullable DefVar<?, ?> op) {
    if (op == null) return;
    buffer.append(new HighlightResult.Symbol(LspRange.toRange(sourcePos), kindOf(op.concrete)));
  }

  private void visitBind(@NotNull DynamicSeq<HighlightResult.Symbol> buffer, @NotNull OpDecl.BindBlock bindBlock) {
    if (bindBlock == OpDecl.BindBlock.EMPTY) return;
    bindBlock.loosers().view().zip(bindBlock.resolvedLoosers().value.view())
      .forEach(tup -> visitOperator(buffer, tup._1.sourcePos(), tup._2));
    bindBlock.tighters().view().zip(bindBlock.resolvedTighters().value.view())
      .forEach(tup -> visitOperator(buffer, tup._1.sourcePos(), tup._2));
  }
  // endregion
}
