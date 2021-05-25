// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.lsp.highlight;

import org.aya.api.error.SourcePos;
import org.aya.api.ref.DefVar;
import org.aya.concrete.*;
import org.aya.concrete.visitor.StmtConsumer;
import org.aya.core.def.DataDef;
import org.aya.core.def.FnDef;
import org.aya.core.def.PrimDef;
import org.aya.core.def.StructDef;
import org.aya.lsp.LspRange;
import org.eclipse.lsp4j.Range;
import org.glavo.kala.collection.immutable.ImmutableSeq;
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

  private void visitClauses(@NotNull ImmutableSeq<Pattern.Clause> clauses, @NotNull Buffer<Symbol> buffer) {
    clauses.forEach(c -> {
      // c.patterns.forEach(p -> p.accept(this, buffer));
      c.expr.forEach(expr -> expr.accept(this, buffer));
    });
  }

  private void visitTele(@NotNull ImmutableSeq<Expr.Param> telescope, @NotNull Buffer<Symbol> buffer) {
    telescope.forEach(p -> {
      var type = p.type();
      if (type != null) type.accept(this, buffer);
    });
  }

  @Override public Unit visitData(@NotNull Decl.DataDecl decl, @NotNull Buffer<Symbol> buffer) {
    buffer.append(new Symbol(rangeOf(decl), Symbol.Kind.DataDef));
    visitTele(decl.telescope, buffer);
    decl.result.accept(this, buffer);
    decl.body.forEach(ctor -> ctor.accept(this, buffer));
    return Unit.unit();
  }

  @Override public Unit visitCtor(@NotNull Decl.DataCtor ctor, @NotNull Buffer<Symbol> buffer) {
    buffer.append(new Symbol(rangeOf(ctor), Symbol.Kind.ConDef));
    visitTele(ctor.telescope, buffer);
    visitClauses(ctor.clauses, buffer);
    return Unit.unit();
  }

  @Override public Unit visitStruct(@NotNull Decl.StructDecl decl, @NotNull Buffer<Symbol> buffer) {
    buffer.append(new Symbol(rangeOf(decl), Symbol.Kind.StructDef));
    visitTele(decl.telescope, buffer);
    decl.result.accept(this, buffer);
    decl.fields.forEach(f -> f.accept(this, buffer));
    return Unit.unit();
  }

  @Override public Unit visitField(@NotNull Decl.StructField field, @NotNull Buffer<Symbol> buffer) {
    buffer.append(new Symbol(rangeOf(field), Symbol.Kind.FieldDef));
    visitTele(field.telescope, buffer);
    field.result.accept(this, buffer);
    field.body.forEach(t -> t.accept(this, buffer));
    visitClauses(field.clauses, buffer);
    return Unit.unit();
  }

  @Override public Unit visitFn(@NotNull Decl.FnDecl decl, @NotNull Buffer<Symbol> buffer) {
    buffer.append(new Symbol(rangeOf(decl), Symbol.Kind.FnDef));
    visitTele(decl.telescope, buffer);
    decl.result.accept(this, buffer);
    decl.body.forEach(t -> t.accept(this, buffer), ms -> visitClauses(ms, buffer));
    return Unit.unit();
  }

  @Override public Unit visitPrim(@NotNull Decl.PrimDecl decl, @NotNull Buffer<Symbol> buffer) {
    buffer.append(new Symbol(rangeOf(decl), Symbol.Kind.PrimDef));
    visitTele(decl.telescope, buffer);
    if (decl.result != null) decl.result.accept(this, buffer);
    return Unit.unit();
  }

  @Override public Unit visitLevels(Generalize.@NotNull Levels levels, @NotNull Buffer<Symbol> buffer) {
    for (var level : levels.levels())
      buffer.append(new Symbol(LspRange.toRange(level.sourcePos()), Symbol.Kind.Generalize));
    return Unit.unit();
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
    if (ref.core instanceof FnDef) buffer.append(new Symbol(LspRange.toRange(headPos), Symbol.Kind.FnCall));
    else if (ref.core instanceof PrimDef) buffer.append(new Symbol(LspRange.toRange(headPos), Symbol.Kind.PrimCall));
    else if (ref.core instanceof DataDef) buffer.append(new Symbol(LspRange.toRange(headPos), Symbol.Kind.DataCall));
    else if (ref.core instanceof DataDef.Ctor)
      buffer.append(new Symbol(LspRange.toRange(headPos), Symbol.Kind.ConCall));
    else if (ref.core instanceof StructDef)
      buffer.append(new Symbol(LspRange.toRange(headPos), Symbol.Kind.StructCall));
    else if (ref.core instanceof StructDef.Field)
      buffer.append(new Symbol(LspRange.toRange(headPos), Symbol.Kind.FieldCall));
  }

  // endregion

  // region pattern
  // endregion

  // region import, open, module

  @Override public Unit visitImport(Stmt.@NotNull ImportStmt cmd, @NotNull Buffer<Symbol> buffer) {
    buffer.append(new Symbol(LspRange.toRange(cmd.sourcePos()), Symbol.Kind.ModuleDef));
    return Unit.unit();
  }

  @Override public Unit visitOpen(Stmt.@NotNull OpenStmt cmd, @NotNull Buffer<Symbol> buffer) {
    buffer.append(new Symbol(LspRange.toRange(cmd.sourcePos()), Symbol.Kind.ModuleDef));
    return Unit.unit();
  }

  @Override public Unit visitModule(Stmt.@NotNull ModuleStmt mod, @NotNull Buffer<Symbol> buffer) {
    buffer.append(new Symbol(LspRange.toRange(mod.sourcePos()), Symbol.Kind.ModuleDef));
    return Unit.unit();
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
    return Unit.unit();
  }

  // endregion
}
