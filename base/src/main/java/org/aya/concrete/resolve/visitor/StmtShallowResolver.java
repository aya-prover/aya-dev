// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.resolve.visitor;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.tuple.Tuple2;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.resolve.ResolveInfo;
import org.aya.concrete.resolve.context.Context;
import org.aya.concrete.resolve.context.ModuleContext;
import org.aya.concrete.resolve.context.NoExportContext;
import org.aya.concrete.resolve.context.PhysicalModuleContext;
import org.aya.concrete.resolve.error.ModNotFoundError;
import org.aya.concrete.resolve.module.ModuleLoader;
import org.aya.concrete.stmt.*;
import org.jetbrains.annotations.NotNull;

/**
 * simply adds all top-level names to the context
 *
 * @author re-xyr
 */
public record StmtShallowResolver(
  @NotNull ModuleLoader loader,
  @NotNull ResolveInfo resolveInfo
) {
  public void resolveStmt(@NotNull SeqLike<Stmt> stmts, ModuleContext context) {
    stmts.forEach(stmt -> resolveStmt(stmt, context));
  }

  public void resolveStmt(@NotNull Stmt stmt, @NotNull ModuleContext context) {
    switch (stmt) {
      case Command.Module mod -> {
        var newCtx = context.derive(mod.name());
        resolveStmt(mod.contents(), newCtx);
        context.importModules(ImmutableSeq.of(mod.name()), mod.accessibility(), newCtx.exports, mod.sourcePos());
      }
      case Command.Import cmd -> {
        var ids = cmd.path().ids();
        var success = loader.load(ids);
        if (success == null) context.reportAndThrow(new ModNotFoundError(cmd.path().ids(), cmd.sourcePos()));
        var mod = (PhysicalModuleContext) success.thisModule(); // this cast should never fail
        resolveInfo.imports().append(success);
        context.importModules(cmd.path().ids(), Stmt.Accessibility.Private, mod.exports, cmd.sourcePos());
      }
      case Command.Open cmd -> context.openModule(
        cmd.path().ids(),
        cmd.accessibility(),
        cmd.useHide()::uses,
        MutableHashMap.create(), // TODO handle renaming
        cmd.sourcePos()
      );
      case Remark remark -> remark.ctx = context;
      case Generalize.Levels levels -> {
        for (var level : levels.levels()) {
          var genVar = level.data();
          context.addGlobalSimple(levels.accessibility(), genVar, level.sourcePos());
        }
      }
      case Generalize.Variables variables -> {
        variables.ctx = context;
        for (var variable : variables.variables)
          context.addGlobalSimple(variables.accessibility(), variable, variable.sourcePos);
      }
      case Sample.Working example -> resolveStmt(example.delegate(), exampleContext(context));
      case Sample.Counter example -> {
        var childCtx = exampleContext(context).derive("counter");
        var delegate = example.delegate();
        delegate.ctx = childCtx;
        childCtx.addGlobalSimple(Stmt.Accessibility.Private, delegate.ref(), delegate.sourcePos);
      }
      case Decl.DataDecl decl -> {
        resolveDecl(decl, context);
        var dataInnerCtx = context.derive(decl.ref().name());
        var ctorSymbols = decl.body.map(ctor -> {
          resolveCtor(ctor, dataInnerCtx);
          return Tuple2.of(ctor.ref.name(), ctor.ref);
        });
        context.importModules(
          ImmutableSeq.of(decl.ref().name()),
          decl.accessibility(),
          MutableHashMap.of(
            Context.TOP_LEVEL_MOD_NAME,
            MutableHashMap.from(ctorSymbols)),
          decl.sourcePos()
        );
        decl.ctx = dataInnerCtx;
        resolveBind(decl.bindBlock, dataInnerCtx);
      }
      case Decl.StructDecl decl -> {
        resolveDecl(decl, context);
        var structInnerCtx = context.derive(decl.ref().name());
        decl.fields.forEach(field -> resolveField(field, structInnerCtx));
        decl.ctx = structInnerCtx;
        resolveBind(decl.bindBlock, structInnerCtx);
      }
      case Decl.FnDecl decl -> {
        resolveDecl(decl, context);
        resolveBind(decl.bindBlock, context);
      }
      case Decl.PrimDecl decl -> resolveDecl(decl, context);
    }
  }

  private void resolveBind(@NotNull BindBlock bind, @NotNull ModuleContext context) {
    if (bind != BindBlock.EMPTY) bind.context().value = context;
  }

  private void resolveDecl(@NotNull Decl decl, @NotNull ModuleContext context) {
    decl.ref().module = context.moduleName();
    context.addGlobalSimple(decl.accessibility(), decl.ref(), decl.sourcePos());
    decl.ctx = context;
  }

  private @NotNull NoExportContext exampleContext(@NotNull ModuleContext context) {
    if (context instanceof PhysicalModuleContext physical)
      return physical.exampleContext();
    else throw new IllegalArgumentException("Invalid context: " + context);
  }

  private void resolveCtor(@NotNull Decl.DataCtor ctor, @NotNull ModuleContext context) {
    ctor.ref().module = context.moduleName();
    context.addGlobalSimple(Stmt.Accessibility.Public, ctor.ref, ctor.sourcePos);
    resolveBind(ctor.bindBlock, context);
  }

  private void resolveField(@NotNull Decl.StructField field, @NotNull ModuleContext context) {
    field.ref().module = context.moduleName();
    context.addGlobalSimple(Stmt.Accessibility.Public, field.ref, field.sourcePos);
    resolveBind(field.bindBlock, context);
  }
}
