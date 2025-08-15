// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.visitor;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.ResolvingStmt;
import org.aya.resolve.context.ModuleContext;
import org.aya.resolve.context.NoExportContext;
import org.aya.resolve.context.PhysicalModuleContext;
import org.aya.resolve.error.NameProblem;
import org.aya.resolve.error.PrimResolveError;
import org.aya.resolve.module.ModuleLoader;
import org.aya.syntax.concrete.stmt.*;
import org.aya.syntax.concrete.stmt.decl.*;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.PrimDef;
import org.aya.syntax.ref.DefVar;
import org.aya.syntax.ref.QPath;
import org.aya.util.Panic;
import org.aya.util.binop.Assoc;
import org.aya.util.binop.OpDecl;
import org.aya.util.reporter.Reporter;
import org.aya.util.reporter.SuppressingReporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * TODO: turn into record
 * simply adds all top-level names to the context
 */
public final class StmtPreResolver {
  private final @NotNull ModuleLoader loader;
  private final @NotNull ResolveInfo resolveInfo;

  public StmtPreResolver(@NotNull ModuleLoader loader, @NotNull ResolveInfo resolveInfo) {
    this.loader = loader;
    this.resolveInfo = resolveInfo;
  }

  /// Resolve {@link Stmt}s under {@param context}.
  ///
  /// @return the context of the body of each {@link Stmt}, where imports and opens are stripped.
  public ImmutableSeq<ResolvingStmt> resolveStmt(@NotNull ImmutableSeq<Stmt> stmts, ModuleContext context) {
    return stmts.mapNotNull(stmt -> resolveStmt(stmt, context));
  }

  public @Nullable ResolvingStmt resolveStmt(@NotNull Stmt stmt, @NotNull ModuleContext context) {
    var thisReporter = resolveInfo.reporter();
    return switch (stmt) {
      case Decl decl -> resolveDecl(decl, context);
      case Command.Module mod -> {
        var wholeModeName = context.modulePath().derive(mod.name());
        // Is there a file level module with path {context.moduleName}::{mod.name} ?
        if (loader.existsFileLevelModule(wholeModeName)) {
          thisReporter.report(new NameProblem.ClashModNameError(wholeModeName, mod.sourcePos()));
          yield null;     // TODO: Is this Problem critical? or we can continue the resolving.
        }

        var newCtx = context.derive(mod.name());
        mod.theContext().set(newCtx);
        var children = resolveStmt(mod.contents(), newCtx);

        context.importModuleContext(ModuleName.This.resolve(mod.name()), newCtx, mod.accessibility(), mod.sourcePos(), thisReporter);

        yield new ResolvingStmt.ModStmt(children);
      }
      case Command.Import cmd -> {
        var modulePath = cmd.path();

        var loaded = loader.load(modulePath);
        switch (loaded.getErrOrNull()) {
          case Resolve -> { yield null; }
          case NotFound -> {
            thisReporter.report(new NameProblem.ModNotFoundError(modulePath, cmd.sourcePos()));
            yield null;
          }
          case null -> { }
        }

        var success = loaded.get();

        var mod = success.thisModule();
        var as = cmd.asName();
        var importedName = as != null ? ModuleName.This.resolve(as.data()) : modulePath.asName();

        context.importModuleContext(importedName, mod, cmd.accessibility(), cmd.sourcePos(), thisReporter);

        var importInfo = new ResolveInfo.ImportInfo(success, cmd.accessibility() == Stmt.Accessibility.Public);
        resolveInfo.imports().put(importedName, importInfo);
        yield null;
      }
      case Command.Open cmd -> {
        var mod = cmd.path();
        var acc = cmd.accessibility();
        var useHide = cmd.useHide();
        var ctx = cmd.openExample() ? exampleContext(context) : context;

        ctx.openModule(mod, acc, cmd.sourcePos(), useHide, thisReporter);

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
        for (var variable : variables.variables) {
          context.defineSymbol(variable, Stmt.Accessibility.Private, variable.sourcePos, thisReporter);
        }
        yield new ResolvingStmt.GenStmt(variables, context);
      }
    };
  }

  private @Nullable ResolvingStmt resolveDecl(@NotNull Decl predecl, @NotNull ModuleContext context) {
    return switch (predecl) {
      case DataDecl decl -> {
        var ctx = resolveTopLevelDecl(decl, context);
        var innerCtx = resolveChildren(decl, ctx, d -> d.body.clauses.view(), (con, mCtx, reporter) -> {
          setupModule(mCtx, con.ref);
          mCtx.defineSymbol(con.ref(), Stmt.Accessibility.Public, con.nameSourcePos(), reporter);
        });
        yield new ResolvingStmt.TopDecl(decl, innerCtx);
      }
      case ClassDecl decl -> {
        var ctx = resolveTopLevelDecl(decl, context);
        var innerCtx = resolveChildren(decl, ctx, d -> d.members.view(), (mem, mCtx, reporter) -> {
          setupModule(mCtx, mem.ref);
          mCtx.defineSymbol(mem.ref(), Stmt.Accessibility.Public, mem.ref().concrete.nameSourcePos(), reporter);
        });
        yield new ResolvingStmt.TopDecl(decl, innerCtx);
      }
      case FnDecl decl -> {
        var resolvedCtx = resolveTopLevelDecl(decl, context);
        yield new ResolvingStmt.TopDecl(decl, resolvedCtx);
      }
      case PrimDecl decl -> {
        var thisReporter = resolveInfo.reporter();
        var factory = resolveInfo.primFactory();
        var name = decl.ref.name();
        var sourcePos = decl.nameSourcePos();

        var primID = PrimDef.ID.find(name);
        if (primID == null) {
          thisReporter.report(new PrimResolveError.UnknownPrim(sourcePos, name));
          yield null;
        } else {
          var lack = factory.checkDependency(primID);
          if (lack.isNotEmpty() && lack.get().isNotEmpty()) {
            thisReporter.report(new PrimResolveError.Dependency(name, lack.get(), sourcePos));
            yield null;
          } else if (factory.isForbiddenRedefinition(primID, false)) {
            thisReporter.report(new PrimResolveError.Redefinition(name, sourcePos));
            yield null;
          }

          factory.factory(primID, decl.ref);
        }

        // whatever success or not, we resolve the decl
        var resolvedCtx = resolveTopLevelDecl(decl, context);
        yield new ResolvingStmt.TopDecl(decl, resolvedCtx);
      }
      default -> Panic.unreachable();
    };
  }

  public static Reporter suppress(@NotNull Reporter reporter, @NotNull Decl decl) {
    var suppressInfo = decl.pragmaInfo.suppressWarn;
    if (suppressInfo == null) return reporter;

    var suppresses = suppressInfo.args();
    if (suppresses.isEmpty()) return reporter;
    var r = new SuppressingReporter(reporter);
    suppresses.forEach(suppress -> {
      switch (suppress.data()) {
        case LocalShadow -> r.suppress(NameProblem.ShadowingWarn.class);
      }
    });

    return r;
  }

  @FunctionalInterface
  private interface ChildResolver<T> {
    void accept(@NotNull T child, @NotNull ModuleContext context, @NotNull Reporter reporter);
  }

  /**
   * pre-resolve children of {@param decl}
   *
   * @param decl          the top-level decl
   * @param context       the context where {@param decl} lives in
   * @param childrenGet   the children of {@param decl}
   * @param childResolver perform resolve on the child of {@param decl}
   * @return the module context of {@param decl}, it should be a sub-module of {@param context}
   */
  private <D extends Decl, Child> ModuleContext resolveChildren(
    @NotNull D decl,
    @NotNull ModuleContext context,
    @NotNull Function<D, SeqView<Child>> childrenGet,
    @NotNull ChildResolver<Child> childResolver
  ) {
    var thisReporter = resolveInfo.reporter();
    var innerReporter = suppress(thisReporter, decl);
    var innerCtx = context.derive(decl.ref().name());
    childrenGet.apply(decl).forEach(child -> childResolver.accept(child, innerCtx, innerReporter));
    var module = decl.ref().name();
    context.importModule(
      ModuleName.This.resolve(module),
      innerCtx.exports(),
      decl.accessibility(),
      decl.nameSourcePos(),
      thisReporter
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

    ctx.defineSymbol(decl.ref(), decl.accessibility(), decl.nameSourcePos(), resolveInfo.reporter());
    return ctx;
  }

  private void setupModule(ModuleContext ctx, DefVar<?, ?> ref) {
    ref.module = new QPath(ctx.modulePath(), resolveInfo.modulePath().size());
  }
}
