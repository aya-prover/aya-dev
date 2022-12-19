// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.visitor;

import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.tuple.Tuple;
import org.aya.concrete.stmt.*;
import org.aya.core.def.PrimDef;
import org.aya.generic.util.InternalException;
import org.aya.ref.DefVar;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.*;
import org.aya.resolve.error.NameProblem;
import org.aya.resolve.error.PrimResolveError;
import org.aya.resolve.module.ModuleLoader;
import org.aya.util.binop.Assoc;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
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
      case Decl decl -> resolveDecl(decl, context);
      case Command.Module mod -> {
        var newCtx = context.derive(mod.name());
        resolveStmt(mod.contents(), newCtx);
        context.importModules(ImmutableSeq.of(mod.name()), mod.accessibility(), newCtx.exports, mod.sourcePos());
      }
      case Command.Import cmd -> {
        var ids = cmd.path().ids();
        var success = loader.load(ids);
        if (success == null)
          context.reportAndThrow(new NameProblem.ModNotFoundError(cmd.path().ids(), cmd.sourcePos()));
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
          useHide.list().map(x -> new WithPos<>(x.sourcePos(), x.id())),
          useHide.renaming(),
          cmd.sourcePos(),
          useHide.strategy() == UseHide.Strategy.Using);
        // open necessities from imported modules (not submodules)
        // because the module itself and its submodules share the same ResolveInfo
        resolveInfo.imports().getOption(mod).ifDefined(modResolveInfo -> {
          if (acc == Stmt.Accessibility.Public) resolveInfo.reExports().put(mod, useHide);
          resolveInfo.open(modResolveInfo, cmd.sourcePos(), acc);
        });
        // renaming as infix
        if (useHide.strategy() == UseHide.Strategy.Using) useHide.list().forEach(use -> {
          if (use.asAssoc() == Assoc.Invalid) return;
          var symbol = ctx.getQualifiedLocalMaybe(mod, use.id(), SourcePos.NONE);
          assert symbol instanceof DefVar<?, ?>;
          var defVar = (DefVar<?, ?>) symbol;
          var renamedOpDecl = new ResolveInfo.RenamedOpDecl(new OpDecl.OpInfo(use.asName(), use.asAssoc()));
          var bind = use.asBind();
          if (bind != BindBlock.EMPTY) bind.context().set(ctx);
          resolveInfo.renameOp(defVar, renamedOpDecl, bind, true);
        });
      }
      case Generalize variables -> {
        variables.ctx = context;
        for (var variable : variables.variables)
          context.addGlobalSimple(variables.accessibility(), variable, variable.sourcePos);
      }
    }
  }

  private void resolveDecl(@NotNull Decl predecl, @NotNull ModuleContext context) {
    switch (predecl) {
      case ClassDecl classDecl -> throw new UnsupportedOperationException("not implemented yet");
      case TeleDecl.DataDecl decl -> {
        var ctx = resolveTopLevelDecl(decl, context);
        var innerCtx = resolveChildren(decl, decl, ctx, d -> d.body.view(), this::resolveDecl);
        resolveOpInfo(decl, innerCtx);
      }
      case TeleDecl.StructDecl decl -> {
        var ctx = resolveTopLevelDecl(decl, context);
        var innerCtx = resolveChildren(decl, decl, ctx, s -> s.fields.view(), this::resolveDecl);
        resolveOpInfo(decl, innerCtx);
      }
      case TeleDecl.FnDecl decl -> {
        var ctx = resolveTopLevelDecl(decl, context);
        resolveOpInfo(decl, ctx);
      }
      case TeleDecl.PrimDecl decl -> {
        var factory = resolveInfo.primFactory();
        var name = decl.ref.name();
        var sourcePos = decl.sourcePos;
        var primID = PrimDef.ID.find(name);
        if (primID == null) context.reportAndThrow(new PrimResolveError.UnknownPrim(sourcePos, name));
        var lack = factory.checkDependency(primID);
        if (lack.isNotEmpty() && lack.get().isNotEmpty())
          context.reportAndThrow(new PrimResolveError.Dependency(name, lack.get(), sourcePos));
        else if (factory.have(primID) && !factory.suppressRedefinition())
          context.reportAndThrow(new PrimResolveError.Redefinition(name, sourcePos));
        factory.factory(primID, decl.ref);
        resolveTopLevelDecl(decl, context);
      }
      case TeleDecl.DataCtor ctor -> {
        ctor.ref().module = context.moduleName();
        context.addGlobalSimple(Stmt.Accessibility.Public, ctor.ref, ctor.sourcePos);
        resolveOpInfo(ctor, context);
      }
      case TeleDecl.StructField field -> {
        field.ref().module = context.moduleName();
        context.addGlobalSimple(Stmt.Accessibility.Public, field.ref, field.sourcePos);
        resolveOpInfo(field, context);
      }
    }
  }

  private <D extends Decl, Child extends Decl> ModuleContext resolveChildren(
    @NotNull D decl,
    @NotNull Decl.TopLevel proof,
    @NotNull ModuleContext context,
    @NotNull Function<D, SeqView<Child>> childrenGet,
    @NotNull BiConsumer<Child, ModuleContext> childResolver
  ) {
    assert decl == proof;
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
        new ModuleExport(MutableHashMap.from(children))),
      decl.sourcePos()
    );
    proof.setCtx(innerCtx);
    return innerCtx;
  }

  private void resolveOpInfo(@NotNull Decl decl, @NotNull ModuleContext context) {
    var bind = decl.bindBlock();
    if (bind != BindBlock.EMPTY) bind.context().set(context);
    if (decl.opInfo() != null) {
      var ref = decl.ref();
      ref.opDecl = decl;
    }
  }

  private <D extends Decl & Decl.TopLevel> @NotNull ModuleContext
  resolveTopLevelDecl(@NotNull D decl, @NotNull ModuleContext context) {
    var ctx = switch (decl.personality()) {
      case NORMAL -> context;
      case EXAMPLE -> exampleContext(context);
      case COUNTEREXAMPLE -> exampleContext(context).derive(decl.ref().name());
    };
    decl.setCtx(ctx);
    decl.ref().module = ctx.moduleName();
    ctx.addGlobalSimple(decl.accessibility(), decl.ref(), decl.sourcePos());
    return ctx;
  }

  private @NotNull NoExportContext exampleContext(@NotNull ModuleContext context) {
    if (context instanceof PhysicalModuleContext physical)
      return physical.exampleContext();
    else throw new InternalException("Invalid context: " + context);
  }
}
