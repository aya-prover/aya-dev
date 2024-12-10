// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.visitor;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.ResolvingStmt;
import org.aya.resolve.context.ModuleContext;
import org.aya.resolve.context.NoExportContext;
import org.aya.resolve.context.PhysicalModuleContext;
import org.aya.resolve.context.ReporterContext;
import org.aya.resolve.error.NameProblem;
import org.aya.resolve.error.PrimResolveError;
import org.aya.resolve.module.ModuleLoader;
import org.aya.syntax.concrete.stmt.*;
import org.aya.syntax.concrete.stmt.decl.*;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.PrimDef;
import org.aya.syntax.ref.DefVar;
import org.aya.syntax.ref.QPath;
import org.aya.util.binop.Assoc;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.Panic;
import org.aya.util.reporter.Reporter;
import org.aya.util.reporter.SuppressingReporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * simply adds all top-level names to the context
 */
public record StmtPreResolver(@NotNull ModuleLoader loader, @NotNull ResolveInfo resolveInfo) {
  /**
   * Resolve {@link Stmt}s under {@param context}.
   *
   * @return the context of the body of each {@link Stmt}, where imports and opens are stripped.
   */
  public ImmutableSeq<ResolvingStmt> resolveStmt(@NotNull ImmutableSeq<Stmt> stmts, ModuleContext context) {
    return stmts.mapNotNull(stmt -> resolveStmt(stmt, context));
  }

  public @Nullable ResolvingStmt resolveStmt(@NotNull Stmt stmt, @NotNull ModuleContext context) {
    return switch (stmt) {
      case Decl decl -> resolveDecl(decl, context);
      case Command.Module mod -> {
        var wholeModeName = context.modulePath().derive(mod.name());
        // Is there a file level module with path {context.moduleName}::{mod.name} ?
        if (loader.existsFileLevelModule(wholeModeName)) {
          context.reportAndThrow(new NameProblem.ClashModNameError(wholeModeName, mod.sourcePos()));
        }
        var newCtx = context.derive(mod.name());
        var children = resolveStmt(mod.contents(), newCtx);
        context.importModuleContext(ModuleName.This.resolve(mod.name()), newCtx, mod.accessibility(), mod.sourcePos());
        yield new ResolvingStmt.ModStmt(children);
      }
      case Command.Import cmd -> {
        var modulePath = cmd.path();
        var success = loader.load(modulePath);
        if (success == null)
          context.reportAndThrow(new NameProblem.ModNotFoundError(modulePath, cmd.sourcePos()));
        var mod = success.thisModule();
        var as = cmd.asName();
        var importedName = as != null ? ModuleName.This.resolve(as) : modulePath.asName();
        context.importModuleContext(importedName, mod, cmd.accessibility(), cmd.sourcePos());
        var importInfo = new ResolveInfo.ImportInfo(success, cmd.accessibility() == Stmt.Accessibility.Public);
        resolveInfo.imports().put(importedName, importInfo);
        yield null;
      }
      case Command.Open cmd -> {
        var mod = cmd.path();
        var acc = cmd.accessibility();
        var useHide = cmd.useHide();
        var ctx = cmd.openExample() ? exampleContext(context) : context;
        ctx.openModule(mod, acc, cmd.sourcePos(), useHide);
        // open necessities from imported modules (not submodules)
        // because the module itself and its submodules share the same ResolveInfo
        resolveInfo.imports().getOption(mod).ifDefined(modResolveInfo -> {
          if (acc == Stmt.Accessibility.Public) resolveInfo.reExports().put(mod, useHide);
          resolveInfo.open(modResolveInfo.resolveInfo(), cmd.sourcePos(), acc);
        });
        // renaming as infix
        if (useHide.strategy() == UseHide.Strategy.Using) useHide.list().forEach(use -> {
          // skip if there is no `as` or it is qualified.
          if (use.asAssoc() == Assoc.Unspecified) return;
          // In case of qualified, it must be a module, not a definition.
          if (use.id().component() != ModuleName.This) return;
          var symbol = ctx.modules().get(mod).symbols().get(use.id().name());
          var asName = use.asName().getOrDefault(use.id().name());
          var renamedOpDecl = new ResolveInfo.RenamedOpDecl(new OpDecl.OpInfo(asName, use.asAssoc()));
          var bind = use.asBind();
          resolveInfo.renameOp(ctx, AnyDef.fromVar(symbol), renamedOpDecl, bind, true);
        });
        yield null;
      }
      case Generalize variables -> {
        for (var variable : variables.variables)
          context.defineSymbol(variable, Stmt.Accessibility.Private, variable.sourcePos);
        yield new ResolvingStmt.GenStmt(variables);
      }
    };
  }

  private @NotNull ResolvingStmt resolveDecl(@NotNull Decl predecl, @NotNull ModuleContext context) {
    return switch (predecl) {
      case DataDecl decl -> {
        var ctx = resolveTopLevelDecl(decl, context);
        var innerCtx = resolveChildren(decl, ctx, d -> d.body.view(), (con, mCtx) -> {
          setupModule(mCtx, con.ref);
          mCtx.defineSymbol(con.ref(), Stmt.Accessibility.Public, con.sourcePos());
        });
        yield new ResolvingStmt.TopDecl(decl, innerCtx);
      }
      case ClassDecl decl -> {
        var ctx = resolveTopLevelDecl(decl, context);
        var innerCtx = resolveChildren(decl, ctx, d -> d.members.view(), (mem, mCtx) -> {
          setupModule(mCtx, mem.ref);
          mCtx.defineSymbol(mem.ref(), Stmt.Accessibility.Public, mem.ref().concrete.sourcePos());
        });
        yield new ResolvingStmt.TopDecl(decl, innerCtx);
      }
      case FnDecl decl -> {
        var ctx = resolveTopLevelDecl(decl, context);
        var hijackedCtx = new ReporterContext(ctx, suppress(context.reporter(), decl));
        yield new ResolvingStmt.TopDecl(decl, hijackedCtx);
      }
      case PrimDecl decl -> {
        var factory = resolveInfo.primFactory();
        var name = decl.ref.name();
        var sourcePos = decl.sourcePos();
        var primID = PrimDef.ID.find(name);
        if (primID == null) context.reportAndThrow(new PrimResolveError.UnknownPrim(sourcePos, name));
        var lack = factory.checkDependency(primID);
        if (lack.isNotEmpty() && lack.get().isNotEmpty())
          context.reportAndThrow(new PrimResolveError.Dependency(name, lack.get(), sourcePos));
        else if (factory.isForbiddenRedefinition(primID, false))
          context.reportAndThrow(new PrimResolveError.Redefinition(name, sourcePos));
        factory.factory(primID, decl.ref);
        var resolvedCtx = resolveTopLevelDecl(decl, context);
        yield new ResolvingStmt.TopDecl(decl, resolvedCtx);
      }
      default -> Panic.unreachable();
    };
  }

  private static Reporter suppress(@NotNull Reporter reporter, @NotNull Decl decl) {
    if (decl.suppresses.isEmpty()) return reporter;
    var r = new SuppressingReporter(reporter);
    decl.suppresses.forEach(suppress -> {
      switch (suppress) {
        case Shadowing -> r.suppress(NameProblem.ShadowingWarn.class);
      }
    });
    return r;
  }

  /**
   * pre-resolve children of {@param decl}
   *
   * @param decl          the top-level decl
   * @param context       the context where {@paran decl} lives in
   * @param childrenGet   the children of {@param decl}
   * @param childResolver perform resolve on the child of {@param decl}
   * @return the module context of {@param decl}, it should be a sub-module of {@param context}
   */
  private <D extends Decl, Child> PhysicalModuleContext resolveChildren(
    @NotNull D decl,
    @NotNull ModuleContext context,
    @NotNull Function<D, SeqView<Child>> childrenGet,
    @NotNull BiConsumer<Child, ModuleContext> childResolver
  ) {
    var innerCtx = context.derive(decl.ref().name(), suppress(context.reporter(), decl));
    childrenGet.apply(decl).forEach(child -> childResolver.accept(child, innerCtx));
    var module = decl.ref().name();
    context.importModule(
      ModuleName.This.resolve(module),
      innerCtx.exports,
      decl.accessibility(),
      decl.sourcePos()
    );
    return innerCtx;
  }

  public static @NotNull NoExportContext exampleContext(@NotNull ModuleContext context) {
    return context instanceof PhysicalModuleContext physical ? physical.exampleContext() : Panic.unreachable();
  }

  private <D extends Decl> @NotNull ModuleContext
  resolveTopLevelDecl(@NotNull D decl, @NotNull ModuleContext context) {
    var ctx = decl.isExample ? exampleContext(context) : context;
    setupModule(ctx, decl.ref());
    ctx.defineSymbol(decl.ref(), decl.accessibility(), decl.sourcePos());
    return ctx;
  }
  private void setupModule(ModuleContext ctx, DefVar<?, ?> ref) {
    ref.module = new QPath(ctx.modulePath(), resolveInfo.modulePath().size());
  }
}
