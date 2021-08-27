// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.visitor;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.tuple.Tuple2;
import kala.tuple.Unit;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.Var;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.resolve.context.Context;
import org.aya.concrete.resolve.context.ModuleContext;
import org.aya.concrete.resolve.context.NoExportContext;
import org.aya.concrete.resolve.context.PhysicalModuleContext;
import org.aya.concrete.resolve.error.ModNotFoundError;
import org.aya.concrete.resolve.module.FileModuleLoader;
import org.aya.concrete.resolve.module.ModuleLoader;
import org.aya.concrete.stmt.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * simply adds all top-level names to the context
 *
 * @author re-xyr
 */
public record StmtShallowResolver(
  @NotNull ModuleLoader loader,
  @Nullable FileModuleLoader.FileResolveInfo resolveInfo
) implements Stmt.Visitor<@NotNull ModuleContext, Unit> {
  @Override public Unit visitModule(Command.@NotNull Module mod, @NotNull ModuleContext context) {
    var newCtx = context.derive(mod.name());
    visitAll(mod.contents(), newCtx);
    context.importModules(ImmutableSeq.of(mod.name()), mod.accessibility(), newCtx.exports, mod.sourcePos());
    return Unit.unit();
  }

  @Override public Unit visitImport(Command.@NotNull Import cmd, @NotNull ModuleContext context) {
    var ids = cmd.path().ids();
    if (resolveInfo != null) resolveInfo.imports().append(ids);
    var success = loader.load(ids);
    if (success == null) context.reportAndThrow(new ModNotFoundError(cmd.path().ids(), cmd.sourcePos()));
    context.importModules(cmd.path().ids(), Stmt.Accessibility.Private, success, cmd.sourcePos());
    return Unit.unit();
  }

  @Override public Unit visitOpen(Command.@NotNull Open cmd, @NotNull ModuleContext context) {
    context.openModule(
      cmd.path().ids(),
      cmd.accessibility(),
      cmd.useHide()::uses,
      MutableHashMap.of(), // TODO handle renaming
      cmd.sourcePos()
    );
    return Unit.unit();
  }

  @Override public Unit visitBind(Command.@NotNull Bind bind, @NotNull ModuleContext context) {
    bind.context().value = context;
    return Unit.unit();
  }

  @Override public Unit visitRemark(@NotNull Remark remark, @NotNull ModuleContext context) {
    remark.ctx = context;
    return Unit.unit();
  }

  private void visitOperator(@NotNull ModuleContext context, @NotNull OpDecl opDecl,
                             Stmt.@NotNull Accessibility accessibility, @NotNull Var ref,
                             @NotNull SourcePos sourcePos) {
    var op = opDecl.asOperator();
    if (op != null && op.name() != null) context.addGlobal(
      Context.TOP_LEVEL_MOD_NAME,
      op.name(),
      accessibility,
      ref,
      sourcePos
    );
  }

  private Unit visitDecl(@NotNull Decl decl, @NotNull ModuleContext context) {
    decl.ref().module = context.moduleName();
    context.addGlobalSimple(decl.accessibility(), decl.ref(), decl.sourcePos());
    if (decl instanceof OpDecl opDecl) {
      visitOperator(context, opDecl, decl.accessibility, decl.ref(), decl.sourcePos);
    }
    decl.ctx = context;
    return Unit.unit();
  }

  @Override public Unit visitLevels(Generalize.@NotNull Levels levels, @NotNull ModuleContext context) {
    for (var level : levels.levels()) {
      var genVar = level.data();
      context.addGlobalSimple(levels.accessibility(), genVar, level.sourcePos());
    }
    return Unit.unit();
  }

  @Override public Unit visitData(Decl.@NotNull DataDecl decl, @NotNull ModuleContext context) {
    visitDecl(decl, context);
    var dataInnerCtx = context.derive(decl.ref().name());
    var ctorSymbols = decl.body.map(ctor -> {
      visitCtor(ctor, dataInnerCtx);
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
    return Unit.unit();
  }

  @Override public Unit visitStruct(Decl.@NotNull StructDecl decl, @NotNull ModuleContext context) {
    visitDecl(decl, context);
    var structInnerCtx = context.derive(decl.ref().name());
    decl.fields.forEach(field -> visitField(field, structInnerCtx));
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

  @Override public Unit visitExample(Sample.@NotNull Working example, @NotNull ModuleContext context) {
    example.delegate().accept(this, exampleContext(context));
    return Unit.unit();
  }

  @Override public Unit visitCounterexample(Sample.@NotNull Counter example, @NotNull ModuleContext context) {
    var childCtx = exampleContext(context).derive("counter");
    var delegate = example.delegate();
    delegate.ctx = childCtx;
    childCtx.addGlobalSimple(Stmt.Accessibility.Private, delegate.ref(), delegate.sourcePos);
    return Unit.unit();
  }

  private @NotNull NoExportContext exampleContext(@NotNull ModuleContext context) {
    if (context instanceof PhysicalModuleContext physical)
      return physical.exampleContext();
    else throw new IllegalArgumentException("Invalid context: " + context);
  }

  @Override public Unit visitCtor(@NotNull Decl.DataCtor ctor, @NotNull ModuleContext context) {
    ctor.ref().module = context.moduleName();
    context.addGlobalSimple(Stmt.Accessibility.Public, ctor.ref, ctor.sourcePos);
    visitOperator(context, ctor, Stmt.Accessibility.Public, ctor.ref, ctor.sourcePos);
    return Unit.unit();
  }

  @Override public Unit visitField(@NotNull Decl.StructField field, @NotNull ModuleContext context) {
    field.ref().module = context.moduleName();
    context.addGlobalSimple(Stmt.Accessibility.Public, field.ref, field.sourcePos);
    return Unit.unit();
  }
}
