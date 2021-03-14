// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.visitor;

import org.aya.concrete.Decl;
import org.aya.concrete.Stmt;
import org.aya.concrete.resolve.context.Context;
import org.aya.concrete.resolve.context.ModuleContext;
import org.aya.concrete.resolve.error.ModNotFoundError;
import org.aya.concrete.resolve.module.ModuleLoader;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.tuple.Tuple2;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * simply adds all top-level names to the context
 *
 * @author re-xyr
 */
public final record StmtShallowResolver(@NotNull ModuleLoader loader)
  implements Stmt.Visitor<@NotNull ModuleContext, Unit> {
  @Override
  public Unit visitModule(Stmt.@NotNull ModuleStmt mod, @NotNull ModuleContext context) {
    var newCtx = context.derive();
    visitAll(mod.contents(), newCtx);
    context.importModule(ImmutableSeq.of(mod.name()), mod.accessibility(), newCtx.exports(), mod.sourcePos());
    return Unit.unit();
  }

  @Override
  public Unit visitImport(Stmt.@NotNull ImportStmt cmd, @NotNull ModuleContext context) {
    var success = loader.load(cmd.path());
    if (success == null) context.reportAndThrow(new ModNotFoundError(cmd.path(), cmd.sourcePos()));
    context.importModule(cmd.path(), Stmt.Accessibility.Private, success, cmd.sourcePos());
    return Unit.unit();
  }

  @Override
  public Unit visitOpen(Stmt.@NotNull OpenStmt cmd, @NotNull ModuleContext context) {
    context.openModule(
      cmd.path(),
      cmd.accessibility(),
      cmd.useHide()::uses,
      MutableHashMap.of(), // TODO handle renaming
      cmd.sourcePos()
    );
    return Unit.unit();
  }

  private Unit visitDecl(@NotNull Decl decl, @NotNull ModuleContext context) {
    context.addGlobal(
      Context.TOP_LEVEL_MOD_NAME,
      decl.ref().name(),
      decl.accessibility(),
      decl.ref(),
      decl.sourcePos()
    );
    decl.ctx = context;
    return Unit.unit();
  }

  @Override
  public Unit visitDataDecl(Decl.@NotNull DataDecl decl, @NotNull ModuleContext context) {
    visitDecl(decl, context);
    decl.body.map(ctors -> {
      context.importModule(
        ImmutableSeq.of(decl.ref().name()),
        decl.accessibility(),
        MutableHashMap.of(
          Context.TOP_LEVEL_MOD_NAME,
          MutableHashMap.from(ctors.ctors().toImmutableSeq().map(ctor ->
            Tuple2.of(ctor.ref.name(), ctor.ref)))),
        decl.sourcePos()
      );
      return Unit.unit();
    }, clauses -> {
      throw new UnsupportedOperationException();
    });
    return Unit.unit();
  }

  @Override
  public Unit visitStructDecl(Decl.@NotNull StructDecl decl, @NotNull ModuleContext context) {
    visitDecl(decl, context);
    // TODO[vont]: struct
    return null;
  }

  @Override
  public Unit visitFnDecl(Decl.@NotNull FnDecl decl, @NotNull ModuleContext context) {
    // TODO[xyr]: abuse block currently have no use, so we ignore it for now.
    return visitDecl(decl, context);
  }
}
