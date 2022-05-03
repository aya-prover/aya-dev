// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.visitor;

import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.tuple.Tuple;
import org.aya.concrete.Expr;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.stmt.*;
import org.aya.core.def.PrimDef;
import org.aya.generic.util.InternalException;
import org.aya.ref.Bind;
import org.aya.ref.DefVar;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.Context;
import org.aya.resolve.context.ModuleContext;
import org.aya.resolve.context.NoExportContext;
import org.aya.resolve.context.PhysicalModuleContext;
import org.aya.resolve.error.ModNotFoundError;
import org.aya.resolve.error.PrimDependencyError;
import org.aya.resolve.error.RedefinitionPrimError;
import org.aya.resolve.error.UnknownPrimError;
import org.aya.resolve.module.ModuleLoader;
import org.aya.util.binop.Assoc;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;
import java.util.function.Function;

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
      case ClassDecl classDecl -> throw new UnsupportedOperationException("not implemented yet");
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
        var as = cmd.asName();
        var importedName = as != null ? ImmutableSeq.of(as) : ids;
        context.importModules(importedName, Stmt.Accessibility.Private, mod.exports, cmd.sourcePos());
        resolveInfo.imports().put(importedName, success);
      }
      case Command.Open cmd -> {
        var mod = cmd.path().ids();
        var acc = cmd.accessibility();
        var useHide = cmd.useHide();
        var ctx = cmd.openExample() ? exampleContext(context) : context;
        ctx.openModule(
          mod,
          acc,
          useHide::uses,
          useHide.renaming(),
          cmd.sourcePos());
        // open operator precedence bindings from imported modules (not submodules)
        // because submodules always share the same opSet with file-level resolveInfo.
        var modInfo = resolveInfo.imports().getOption(mod);
        if (modInfo.isDefined()) {
          if (acc == Stmt.Accessibility.Public) resolveInfo.reExports().append(mod);
          resolveInfo.opSet().importBind(modInfo.get().opSet(), cmd.sourcePos());
        }
        // renaming as infix
        if (useHide.strategy() == Command.Open.UseHide.Strategy.Using) useHide.list().forEach(use -> {
          if (use.asAssoc() == Assoc.Invalid) return;
          var symbol = ctx.getQualifiedLocalMaybe(mod, use.id(), SourcePos.NONE);
          assert symbol instanceof DefVar<?, ?>;
          var defVar = (DefVar<?, ?>) symbol;
          var argc = defVar.core != null
            ? defVar.core.telescope().count(Bind::explicit)
            : defVar.concrete.telescope.count(Expr.Param::explicit);
          OpDecl rename = () -> new OpDecl.OpInfo(use.asName(), use.asAssoc(), argc);
          defVar.opDeclRename.put(resolveInfo.thisModule().moduleName(), rename);
          var bind = use.asBind();
          if (bind != BindBlock.EMPTY) {
            bind.context().value = ctx;
            resolveInfo.bindBlockRename().put(rename, bind);
          }
        });
      }
      case Remark remark -> remark.ctx = context;
      case Generalize variables -> {
        variables.ctx = context;
        for (var variable : variables.variables)
          context.addGlobalSimple(variables.accessibility(), variable, variable.sourcePos);
      }
      case Decl.DataDecl decl -> {
        var ctx = resolveDecl(decl, context);
        var innerCtx = resolveChildren(decl, ctx, d -> d.body.view(), this::resolveCtor);
        resolveOpInfo(decl, innerCtx);
      }
      case Decl.StructDecl decl -> {
        var ctx = resolveDecl(decl, context);
        var innerCtx = resolveChildren(decl, ctx, s -> s.fields.view(), this::resolveField);
        resolveOpInfo(decl, innerCtx);
      }
      case Decl.FnDecl decl -> {
        var ctx = resolveDecl(decl, context);
        resolveOpInfo(decl, ctx);
      }
      case Decl.PrimDecl decl -> {
        var factory = resolveInfo.primFactory();
        var name = decl.ref.name();
        var sourcePos = decl.sourcePos;
        var primID = PrimDef.ID.find(name);
        if (primID == null) context.reportAndThrow(new UnknownPrimError(sourcePos, name));
        var lack = factory.checkDependency(primID);
        if (lack.isNotEmpty() && lack.get().isNotEmpty())
          context.reportAndThrow(new PrimDependencyError(name, lack.get(), sourcePos));
        else if (factory.have(primID))
          context.reportAndThrow(new RedefinitionPrimError(name, sourcePos));
        factory.factory(primID, decl.ref);
        resolveDecl(decl, context);
      }
    }
  }

  private <D extends Decl, Child extends Signatured> ModuleContext resolveChildren(
    @NotNull D decl,
    @NotNull ModuleContext context,
    @NotNull Function<D, SeqView<Child>> childrenGet,
    @NotNull BiConsumer<Child, ModuleContext> childResolver
  ) {
    var innerCtx = context.derive(decl.ref().name());
    var children = childrenGet.apply(decl).map(child -> {
      childResolver.accept(child, innerCtx);
      return Tuple.of(child.ref().name(), child.ref());
    });
    context.importModules(
      ImmutableSeq.of(decl.ref().name()),
      decl.accessibility(),
      MutableHashMap.of(
        Context.TOP_LEVEL_MOD_NAME,
        MutableHashMap.from(children)),
      decl.sourcePos()
    );
    decl.ctx = innerCtx;
    return innerCtx;
  }

  private void resolveOpInfo(@NotNull Signatured signatured, @NotNull ModuleContext context) {
    var bind = signatured.bindBlock;
    if (bind != BindBlock.EMPTY) bind.context().value = context;
    if (signatured.opInfo != null) {
      var ref = signatured.ref();
      ref.opDecl = signatured;
    }
  }

  private @NotNull ModuleContext resolveDecl(@NotNull Decl decl, @NotNull ModuleContext context) {
    var ctx = switch (decl.personality) {
      case NORMAL -> context;
      case EXAMPLE -> exampleContext(context);
      case COUNTEREXAMPLE -> exampleContext(context).derive(decl.ref().name());
    };
    decl.ctx = ctx;
    decl.ref().module = ctx.moduleName();
    ctx.addGlobalSimple(decl.accessibility(), decl.ref(), decl.sourcePos);
    return ctx;
  }

  private @NotNull NoExportContext exampleContext(@NotNull ModuleContext context) {
    if (context instanceof PhysicalModuleContext physical)
      return physical.exampleContext();
    else throw new InternalException("Invalid context: " + context);
  }

  private void resolveCtor(@NotNull Decl.DataCtor ctor, @NotNull ModuleContext context) {
    ctor.ref().module = context.moduleName();
    context.addGlobalSimple(Stmt.Accessibility.Public, ctor.ref, ctor.sourcePos);
    resolveOpInfo(ctor, context);
  }

  private void resolveField(@NotNull Decl.StructField field, @NotNull ModuleContext context) {
    field.ref().module = context.moduleName();
    context.addGlobalSimple(Stmt.Accessibility.Public, field.ref, field.sourcePos);
    resolveOpInfo(field, context);
  }
}
