// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.lsp.highlight;

import org.aya.api.error.SourcePos;
import org.aya.api.ref.DefVar;
import org.aya.concrete.*;
import org.aya.concrete.visitor.StmtConsumer;
import org.aya.lsp.LspRange;
import org.eclipse.lsp4j.Range;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.tuple.Unit;
import org.glavo.kala.value.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Highlighter implements StmtConsumer<@NotNull Buffer<Symbol>> {
  public static final Highlighter INSTANCE = new Highlighter();

  private @NotNull Range rangeOf(@NotNull Signatured signatured) {
    return LspRange.toRange(signatured.sourcePos());
  }

  // region def, data, struct, prim, levels
  @Override public Unit visitData(@NotNull Decl.DataDecl decl, @NotNull Buffer<Symbol> buffer) {
    buffer.append(new Symbol(rangeOf(decl), Symbol.Kind.DataDef));
    return StmtConsumer.super.visitData(decl, buffer);
  }

  @Override public Unit visitCtor(@NotNull Decl.DataCtor ctor, @NotNull Buffer<Symbol> buffer) {
    buffer.append(new Symbol(rangeOf(ctor), Symbol.Kind.ConDef));
    return StmtConsumer.super.visitCtor(ctor, buffer);
  }

  @Override public Unit visitStruct(@NotNull Decl.StructDecl decl, @NotNull Buffer<Symbol> buffer) {
    buffer.append(new Symbol(rangeOf(decl), Symbol.Kind.StructDef));
    return StmtConsumer.super.visitStruct(decl, buffer);
  }

  @Override public Unit visitField(@NotNull Decl.StructField field, @NotNull Buffer<Symbol> buffer) {
    buffer.append(new Symbol(rangeOf(field), Symbol.Kind.FieldDef));
    return StmtConsumer.super.visitField(field, buffer);
  }

  @Override public Unit visitFn(@NotNull Decl.FnDecl decl, @NotNull Buffer<Symbol> buffer) {
    buffer.append(new Symbol(rangeOf(decl), Symbol.Kind.FnDef));
    return StmtConsumer.super.visitFn(decl, buffer);
  }

  @Override public Unit visitPrim(@NotNull Decl.PrimDecl decl, @NotNull Buffer<Symbol> buffer) {
    buffer.append(new Symbol(rangeOf(decl), Symbol.Kind.PrimDef));
    return StmtConsumer.super.visitPrim(decl, buffer);
  }

  @Override public Unit visitLevels(Generalize.@NotNull Levels levels, @NotNull Buffer<Symbol> buffer) {
    for (var level : levels.levels())
      buffer.append(new Symbol(LspRange.toRange(level.sourcePos()), Symbol.Kind.Generalize));
    return StmtConsumer.super.visitLevels(levels, buffer);
  }
  // endregion

  // region call terms
  @Override public Unit visitRef(@NotNull Expr.RefExpr expr, @NotNull Buffer<Symbol> buffer) {
    if (expr.resolvedVar() instanceof DefVar<?, ?> defVar)
      visitCall(defVar, expr.sourcePos(), buffer);
    return StmtConsumer.super.visitRef(expr, buffer);
  }

  @Override public Unit visitProj(@NotNull Expr.ProjExpr expr, @NotNull Buffer<Symbol> buffer) {
    if (expr.resolvedIx().value instanceof DefVar<?, ?> defVar)
      visitCall(defVar, expr.ix().getRightValue().sourcePos(), buffer);
    return StmtConsumer.super.visitProj(expr, buffer);
  }

  private void visitCall(@NotNull DefVar<?, ?> ref, @NotNull SourcePos headPos, @NotNull Buffer<Symbol> buffer) {
    if (ref.concrete instanceof Decl.FnDecl) buffer.append(new Symbol(LspRange.toRange(headPos), Symbol.Kind.FnCall));
    else if (ref.concrete instanceof Decl.PrimDecl)
      buffer.append(new Symbol(LspRange.toRange(headPos), Symbol.Kind.PrimCall));
    else if (ref.concrete instanceof Decl.DataDecl)
      buffer.append(new Symbol(LspRange.toRange(headPos), Symbol.Kind.DataCall));
    else if (ref.concrete instanceof Decl.DataCtor)
      buffer.append(new Symbol(LspRange.toRange(headPos), Symbol.Kind.ConCall));
    else if (ref.concrete instanceof Decl.StructDecl)
      buffer.append(new Symbol(LspRange.toRange(headPos), Symbol.Kind.StructCall));
    else if (ref.concrete instanceof Decl.StructField)
      buffer.append(new Symbol(LspRange.toRange(headPos), Symbol.Kind.FieldCall));
  }

  // endregion

  // region pattern
  @Override public Unit visitBind(@NotNull Pattern.Bind bind, @NotNull Buffer<Symbol> buffer) {
    if (bind.resolved().value instanceof DefVar<?, ?> defVar)
      visitCall(defVar, bind.sourcePos(), buffer);
    return StmtConsumer.super.visitBind(bind, buffer);
  }

  @Override public Unit visitCtor(@NotNull Pattern.Ctor ctor, @NotNull Buffer<Symbol> buffer) {
    if (ctor.resolved().value instanceof DefVar<?, ?> defVar)
      visitCall(defVar, ctor.name().sourcePos(), buffer);
    return StmtConsumer.super.visitCtor(ctor, buffer);
  }
  // endregion

  // region import, open, module

  @Override public Unit visitImport(Stmt.@NotNull ImportStmt cmd, @NotNull Buffer<Symbol> buffer) {
    buffer.append(new Symbol(LspRange.toRange(cmd.sourcePos()), Symbol.Kind.ModuleDef));
    return StmtConsumer.super.visitImport(cmd, buffer);
  }

  @Override public Unit visitOpen(Stmt.@NotNull OpenStmt cmd, @NotNull Buffer<Symbol> buffer) {
    buffer.append(new Symbol(LspRange.toRange(cmd.sourcePos()), Symbol.Kind.ModuleDef));
    return StmtConsumer.super.visitOpen(cmd, buffer);
  }

  @Override public Unit visitModule(Stmt.@NotNull ModuleStmt mod, @NotNull Buffer<Symbol> buffer) {
    buffer.append(new Symbol(LspRange.toRange(mod.sourcePos()), Symbol.Kind.ModuleDef));
    return StmtConsumer.super.visitModule(mod, buffer);
  }

  private Symbol.@NotNull Kind kindOf(@NotNull Decl.OpDecl opDecl) {
    if (opDecl instanceof Decl.FnDecl) return Symbol.Kind.FnCall;
    else if (opDecl instanceof Decl.StructDecl) return Symbol.Kind.StructCall;
    else if (opDecl instanceof Decl.PrimDecl) return Symbol.Kind.PrimCall;
    else if (opDecl instanceof Decl.DataCtor) return Symbol.Kind.ConCall;
    else throw new IllegalArgumentException("Unsupported operator: " + opDecl.getClass().getName());
  }

  private void visitOperator(@NotNull Buffer<Symbol> buffer, @NotNull SourcePos sourcePos, @NotNull Ref<Decl.@Nullable OpDecl> ref) {
    if (ref.value == null) return;
    buffer.append(new Symbol(LspRange.toRange(sourcePos), kindOf(ref.value)));
  }

  @Override public Unit visitBind(Stmt.@NotNull BindStmt bind, @NotNull Buffer<Symbol> buffer) {
    visitOperator(buffer, bind.op().sourcePos(), bind.resolvedOp());
    visitOperator(buffer, bind.target().sourcePos(), bind.resolvedTarget());
    return StmtConsumer.super.visitBind(bind, buffer);
  }

  // endregion
}
