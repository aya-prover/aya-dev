// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.visitor;

import kala.collection.SeqLike;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.ResolvingStmt;
import org.aya.resolve.context.Context;
import org.aya.resolve.error.NameProblem;
import org.aya.syntax.concrete.stmt.BindBlock;
import org.aya.syntax.concrete.stmt.QualifiedID;
import org.aya.syntax.concrete.stmt.decl.ClassDecl;
import org.aya.syntax.concrete.stmt.decl.DataDecl;
import org.aya.syntax.concrete.stmt.decl.FnDecl;
import org.aya.syntax.concrete.stmt.decl.PrimDecl;
import org.aya.syntax.ref.AnyDefVar;
import org.aya.syntax.ref.DefVar;
import org.aya.util.Panic;
import org.aya.util.binop.OpDecl;
import org.aya.util.reporter.LocalReporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.aya.resolve.ResolvingStmt.*;

public record StmtBinder(@NotNull ResolveInfo info, @NotNull LocalReporter reporter) {
  private void visitBind(@NotNull Context ctx, @NotNull DefVar<?, ?> selfDef, @NotNull BindBlock bind) {
    bind(ctx, bind, selfDef.concrete);
  }

  /**
   * Bind {@param bindBlock} to {@param opSet} in {@param ctx}
   */
  public void bind(@NotNull Context ctx, @NotNull BindBlock bindBlock, OpDecl self) {
    if (bindBlock == BindBlock.EMPTY) return;
    bindBlock.resolvedLoosers().set(bindBlock.loosers().mapNotNull(looser ->
      bind(self, ctx, OpDecl.BindPred.Looser, looser)));
    bindBlock.resolvedTighters().set(bindBlock.tighters().mapNotNull(tighter ->
      bind(self, ctx, OpDecl.BindPred.Tighter, tighter)));
  }

  private @Nullable AnyDefVar bind(
    @NotNull OpDecl self, @NotNull Context ctx,
    @NotNull OpDecl.BindPred pred, @NotNull QualifiedID id
  ) {
    var var = ctx.get(id, reporter);
    assert var != null;
    var opDecl = info.resolveOpDecl(var);
    if (opDecl != null) {
      var success = info.opSet().bind(self, pred, opDecl, id.sourcePos());
      if (success) {
        return var instanceof AnyDefVar defVar ? defVar : null;
      }
    } else {
      reporter.report(new NameProblem.OperatorNameNotFound(id.sourcePos(), id.join()));
    }
    return null;
  }

  public void resolveBind(@NotNull SeqLike<ResolvingStmt> contents) {
    contents.forEach(s -> resolveBind(info.thisModule(), s));
    info.opRename().forEachChecked((_, v) -> {
      if (v.bind() == BindBlock.EMPTY) return;
      bind(info.thisModule(), v.bind(), v.renamed());
    });
  }

  /**
   * @param ctx the context that {@param stmt} binds to
   */
  private void resolveBind(@NotNull Context ctx, @NotNull ResolvingStmt stmt) {
    switch (stmt) {
      case TopDecl(DataDecl decl, var innerCtx) -> {
        decl.body.forEach(con -> resolveBind(innerCtx, new MiscDecl(con)));
        visitBind(ctx, decl.ref, decl.bindBlock());
      }
      case TopDecl(ClassDecl decl, var innerCtx) -> {
        decl.members.forEach(field -> resolveBind(innerCtx, new MiscDecl(field)));
        visitBind(ctx, decl.ref, decl.bindBlock());
      }
      case TopDecl(FnDecl fn, var innerCtx) -> visitBind(innerCtx, fn.ref, fn.bindBlock());
      case MiscDecl(var decl) -> visitBind(ctx, decl.ref(), decl.bindBlock());
      case TopDecl(PrimDecl _, _), GenStmt _ -> { }
      case TopDecl _ -> Panic.unreachable();
      case ModCmd(var stmts, _, _) -> resolveBind(stmts);
      case ImportCmd importCmd -> { }
      case OpenCmd openCmd -> { }
    }
  }
}
