// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.visitor;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.tuple.Tuple2;
import kala.tuple.Unit;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.Var;
import org.aya.concrete.Decl;
import org.aya.concrete.Generalize;
import org.aya.concrete.Stmt;
import org.aya.concrete.resolve.context.Context;
import org.aya.concrete.resolve.context.ModuleContext;
import org.aya.concrete.resolve.error.ModNotFoundError;
import org.aya.concrete.resolve.module.ModuleLoader;
import org.jetbrains.annotations.NotNull;

/**
 * simply adds all top-level names to the context
 *
 * @author re-xyr
 */
public record StmtShallowResolver(@NotNull ModuleLoader loader)
  implements Stmt.Visitor<@NotNull ModuleContext, Unit> {
  @Override public Unit visitModule(Stmt.@NotNull ModuleStmt mod, @NotNull ModuleContext context) {
    var newCtx = context.derive();
    visitAll(mod.contents(), newCtx);
    context.importModule(ImmutableSeq.of(mod.name()), mod.accessibility(), newCtx.exports(), mod.sourcePos());
    return Unit.unit();
  }

  @Override public Unit visitImport(Stmt.@NotNull ImportStmt cmd, @NotNull ModuleContext context) {
    var success = loader.load(cmd.path());
    if (success == null) context.reportAndThrow(new ModNotFoundError(cmd.path(), cmd.sourcePos()));
    context.importModule(cmd.path(), Stmt.Accessibility.Private, success, cmd.sourcePos());
    return Unit.unit();
  }

  @Override public Unit visitOpen(Stmt.@NotNull OpenStmt cmd, @NotNull ModuleContext context) {
    context.openModule(
      cmd.path(),
      cmd.accessibility(),
      cmd.useHide()::uses,
      MutableHashMap.of(), // TODO handle renaming
      cmd.sourcePos()
    );
    return Unit.unit();
  }

  @Override public Unit visitBind(Stmt.@NotNull BindStmt bind, @NotNull ModuleContext context) {
    bind.context().value = context;
    return Unit.unit();
  }

  private void visitOperator(@NotNull ModuleContext context, @NotNull Decl.OpDecl opDecl,
                             Stmt.@NotNull Accessibility accessibility, @NotNull Var ref,
                             @NotNull SourcePos sourcePos) {
    var op = opDecl.asOperator();
    if (op != null && op._1 != null) context.addGlobal(
      Context.TOP_LEVEL_MOD_NAME,
      op._1,
      accessibility,
      ref,
      sourcePos
    );
  }

  private Unit visitDecl(@NotNull Decl decl, @NotNull ModuleContext context) {
    context.addGlobal(
      Context.TOP_LEVEL_MOD_NAME,
      decl.ref().name(),
      decl.accessibility(),
      decl.ref(),
      decl.sourcePos()
    );
    if (decl instanceof Decl.OpDecl opDecl) {
      visitOperator(context, opDecl, decl.accessibility, decl.ref(), decl.sourcePos);
    }
    decl.ctx = context;
    return Unit.unit();
  }

  @Override public Unit visitLevels(Generalize.@NotNull Levels levels, @NotNull ModuleContext context) {
    for (var level : levels.levels()) {
      var genVar = level.data();
      context.addGlobal(
        Context.TOP_LEVEL_MOD_NAME,
        genVar.name(),
        levels.accessibility(),
        genVar,
        level.sourcePos()
      );
    }
    return Unit.unit();
  }

  @Override public Unit visitData(Decl.@NotNull DataDecl decl, @NotNull ModuleContext context) {
    visitDecl(decl, context);
    var dataInnerCtx = context.derive();
    var ctorSymbols = decl.body.toImmutableSeq()
      .map(ctor -> {
        dataInnerCtx.addGlobal(
          Context.TOP_LEVEL_MOD_NAME,
          ctor.ref.name(),
          Stmt.Accessibility.Public,
          ctor.ref,
          ctor.sourcePos
        );
        visitOperator(context, ctor, Stmt.Accessibility.Public, ctor.ref, ctor.sourcePos);
        return Tuple2.of(ctor.ref.name(), ctor.ref);
      });

    context.importModule(
      ImmutableSeq.of(decl.ref().name()),
      decl.accessibility(),
      MutableHashMap.of(
        Context.TOP_LEVEL_MOD_NAME,
        MutableHashMap.from(ctorSymbols)),
      decl.sourcePos()
    );
    decl.ctx = dataInnerCtx;
    return Unit.unit();
  }

  @Override public Unit visitStruct(Decl.@NotNull StructDecl decl, @NotNull ModuleContext context) {
    visitDecl(decl, context);
    var structInnerCtx = context.derive();
    decl.fields.forEach(field -> structInnerCtx.addGlobal(
      Context.TOP_LEVEL_MOD_NAME,
      field.ref.name(),
      Stmt.Accessibility.Public,
      field.ref,
      field.sourcePos
    ));
    decl.ctx = structInnerCtx;
    return Unit.unit();
  }

  @Override public Unit visitFn(Decl.@NotNull FnDecl decl, @NotNull ModuleContext context) {
    // TODO[xyr]: abuse block currently have no use, so we ignore it for now.
    return visitDecl(decl, context);
  }

  @Override public Unit visitPrim(@NotNull Decl.PrimDecl decl, @NotNull ModuleContext moduleContext) {
    visitDecl(decl, moduleContext);
    return Unit.unit();
  }
}
