// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.visitor;

import kala.collection.SeqLike;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.ResolvingStmt;
import org.aya.resolve.context.Context;
import org.aya.resolve.error.NameProblem;
import org.aya.syntax.concrete.stmt.BindBlock;
import org.aya.syntax.concrete.stmt.QualifiedID;
import org.aya.syntax.concrete.stmt.decl.DataCon;
import org.aya.syntax.concrete.stmt.decl.DataDecl;
import org.aya.syntax.concrete.stmt.decl.FnDecl;
import org.aya.syntax.concrete.stmt.decl.PrimDecl;
import org.aya.syntax.ref.DefVar;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.aya.resolve.ResolvingStmt.*;

public record StmtBinder(@NotNull ResolveInfo info) {
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

  private @Nullable DefVar<?, ?> bind(
    @NotNull OpDecl self, @NotNull Context ctx,
    @NotNull OpDecl.BindPred pred, @NotNull QualifiedID id
  ) throws Context.ResolvingInterruptedException {
    var var = ctx.get(id);
    var opDecl = info.resolveOpDecl(var);
    if (opDecl != null) {
      info.opSet().bind(self, pred, opDecl, id.sourcePos());
      return var instanceof DefVar<?, ?> defVar ? defVar : null;
    }

    // make compiler happy 😥
    throw StmtResolver.resolvingInterrupt(info.opSet().reporter,
      new NameProblem.OperatorNameNotFound(id.sourcePos(), id.join()));
  }

  public void resolveBind(@NotNull SeqLike<ResolvingStmt> contents) {
    contents.forEach(s -> resolveBind(info.thisModule(), s));
    info.opRename().forEach((_, v) -> {
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
      case TopDecl(FnDecl fn, var innerCtx) -> visitBind(innerCtx, fn.ref, fn.bindBlock());
      case MiscDecl(DataCon con) -> visitBind(ctx, con.ref, con.bindBlock());
      case TopDecl(PrimDecl _, _), GenStmt _ -> { }
      case TopDecl _, MiscDecl _ -> Panic.unreachable();
      case ModStmt(var stmts) -> resolveBind(stmts);
      // case TeleDecl.ClassMember field -> visitBind(field.ref, field.bindBlock());
      // case ClassDecl decl -> {
      //   decl.members.forEach(field -> resolveBind(field));
      //   visitBind(decl.ref, decl.bindBlock());
      // }
    }
  }
}
