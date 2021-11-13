// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.resolve.visitor;

import kala.tuple.Unit;
import org.aya.api.ref.DefVar;
import org.aya.concrete.desugar.BinOpSet;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.resolve.ResolveInfo;
import org.aya.concrete.resolve.context.Context;
import org.aya.concrete.resolve.error.UnknownOperatorError;
import org.aya.concrete.stmt.*;
import org.jetbrains.annotations.NotNull;

/**
 * Resolve bind blocks, after {@link StmtResolver}
 *
 * @author kiva
 */
public final class BindResolver implements Stmt.Visitor<ResolveInfo, Unit> {
  public static final @NotNull BindResolver INSTANCE = new BindResolver();

  private BindResolver() {
  }

  @Override public Unit visitModule(Command.@NotNull Module mod, ResolveInfo info) {
    visitAll(mod.contents(), info);
    return Unit.unit();
  }

  @Override public Unit visitImport(Command.@NotNull Import cmd, ResolveInfo info) {
    return Unit.unit();
  }

  @Override public Unit visitOpen(Command.@NotNull Open cmd, ResolveInfo info) {
    return Unit.unit();
  }

  public void visitBind(@NotNull OpDecl self, OpDecl.@NotNull BindBlock bind, ResolveInfo info) {
    if (bind == OpDecl.BindBlock.EMPTY) return;
    var ctx = bind.context().value;
    assert ctx != null : "no shallow resolver?";
    var opSet = info.opSet();
    bind.resolvedLoosers().value = bind.loosers().map(looser -> bind(self, opSet, ctx, OpDecl.BindPred.Looser, looser));
    bind.resolvedTighters().value = bind.tighters().map(tighter -> bind(self, opSet, ctx, OpDecl.BindPred.Tighter, tighter));
  }

  private @NotNull DefVar<?, ?> bind(@NotNull OpDecl self, @NotNull BinOpSet opSet, @NotNull Context ctx,
                                     @NotNull OpDecl.BindPred pred, @NotNull QualifiedID id) {
    var var = ctx.get(id);
    if (var instanceof DefVar<?, ?> defVar && defVar.concrete instanceof OpDecl op) {
      opSet.bind(self, pred, op, id.sourcePos());
      return defVar;
    } else {
      opSet.reporter().report(new UnknownOperatorError(id.sourcePos(), id.join()));
      throw new Context.ResolvingInterruptedException();
    }
  }

  @Override public Unit visitCtor(@NotNull Decl.DataCtor ctor, ResolveInfo info) {
    throw new UnsupportedOperationException();
  }

  @Override public Unit visitField(@NotNull Decl.StructField field, ResolveInfo info) {
    throw new UnsupportedOperationException();
  }

  @Override public Unit visitData(Decl.@NotNull DataDecl decl, ResolveInfo info) {
    decl.body.forEach(ctor -> visitBind(ctor, ctor.bindBlock, info));
    visitBind(decl, decl.bindBlock, info);
    return Unit.unit();
  }

  @Override public Unit visitStruct(Decl.@NotNull StructDecl decl, ResolveInfo info) {
    decl.fields.forEach(field -> visitBind(field, field.bindBlock, info));
    visitBind(decl, decl.bindBlock, info);
    return Unit.unit();
  }

  @Override public Unit visitFn(Decl.@NotNull FnDecl decl, ResolveInfo info) {
    visitBind(decl, decl.bindBlock, info);
    return Unit.unit();
  }

  @Override public Unit visitPrim(@NotNull Decl.PrimDecl decl, ResolveInfo info) {
    return Unit.unit();
  }

  @Override public Unit visitLevels(Generalize.@NotNull Levels levels, ResolveInfo info) {
    return Unit.unit();
  }

  @Override public Unit visitExample(Sample.@NotNull Working example, ResolveInfo info) {
    return Unit.unit();
  }

  @Override public Unit visitCounterexample(Sample.@NotNull Counter example, ResolveInfo info) {
    return Unit.unit();
  }

  @Override public Unit visitRemark(@NotNull Remark remark, ResolveInfo info) {
    return Unit.unit();
  }
}
