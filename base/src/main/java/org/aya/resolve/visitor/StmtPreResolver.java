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
import org.aya.resolve.ser.SerImport;
import org.aya.resolve.ser.SerModule;
import org.aya.resolve.ser.SerOpen;
import org.aya.resolve.ser.SerUseHide;
import org.aya.syntax.concrete.stmt.*;
import org.aya.syntax.concrete.stmt.decl.*;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.PrimDef;
import org.aya.syntax.ref.DefVar;
import org.aya.syntax.ref.ModulePath;
import org.aya.syntax.ref.QPath;
import org.aya.util.Panic;
import org.aya.util.binop.Assoc;
import org.aya.util.binop.OpDecl;
import org.aya.util.position.SourcePos;
import org.aya.util.position.WithPos;
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
  public @NotNull ImmutableSeq<ResolvingStmt> resolveStmt(@NotNull ImmutableSeq<Stmt> stmts, ModuleContext context) {
    return stmts.mapNotNull(stmt -> resolveStmt(stmt, context));
  }

  public static ResolvingStmt.@NotNull ModCmd resolveModule(
    @NotNull ModuleContext parent,
    @NotNull Reporter reporter,
    @NotNull SourcePos pos, @NotNull String name,
    @NotNull Function<ModuleContext, ImmutableSeq<ResolvingStmt>> cont
  ) {
    var newCtx = parent.derive(name);
    var children = cont.apply(newCtx);

    parent.importModuleContext(ModuleName.This.resolve(name), newCtx, Stmt.Accessibility.Public, pos, reporter);

    return new ResolvingStmt.ModCmd(children, newCtx, new SerModule(name, children.mapNotNull(it -> {
      if (it instanceof ResolvingStmt.ResolvingCmd cmd) {
        return cmd.cmd();
      } else {
        return null;
      }
    })));
  }

  public record ImportResult(@NotNull ResolveInfo info, @NotNull ModuleName.Qualified importName) { }

  /// @return [ResolveInfo] of imported module
  public static @Nullable ImportResult resolveImport(
    @NotNull ModuleLoader loader,
    @NotNull ModuleContext parent,
    @NotNull Reporter reporter,
    @NotNull SourcePos pos,
    @NotNull ResolveInfo info,
    @NotNull ModulePath modulePath,
    @Nullable WithPos<String> asName,
    @NotNull Stmt.Accessibility accessibility
  ) {
    var loaded = loader.load(modulePath);
    switch (loaded.getErrOrNull()) {
      case Resolve -> { return null; }
      case NotFound -> {
        reporter.report(new NameProblem.ModNotFoundError(modulePath, pos));
        return null;
      }
      case null -> { }
    }

    var success = loaded.get();

    var mod = success.thisModule();
    var importedName = asName != null ? ModuleName.This.resolve(asName.data()) : modulePath.asName();

    parent.importModuleContext(importedName, mod, accessibility, pos, reporter);
    info.primFactory().importFrom(success.primFactory());

    var importInfo = new ResolveInfo.ImportInfo(success, accessibility == Stmt.Accessibility.Public);
    info.imports().put(importedName, importInfo);

    return new ImportResult(success, importedName);
  }

  public static boolean resolveOpen(
    @NotNull ModuleContext parent,
    @NotNull Reporter reporter,
    @NotNull SourcePos pos,
    @NotNull ResolveInfo info,
    @NotNull ModuleName.Qualified mod,
    @NotNull Stmt.Accessibility accessibility,
    @NotNull UseHide useHide,
    boolean example
  ) {
    var ctx = example ? exampleContext(parent) : parent;

    var success = ctx.openModule(mod, accessibility, pos, useHide, reporter);
    if (!success) return false;

    // store top-level re-exports
    // FIXME: this is not enough, because submodule export definitions are not stored
    // TODO: need this no more
    if (ctx == info.thisModule()) {
      if (accessibility == Stmt.Accessibility.Public) info.reExports().put(mod, useHide);
    }
    // open necessities from imported modules (not submodules)
    // because the module itself and its submodules share the same ResolveInfo
    info.imports().getOption(mod).ifDefined(modResolveInfo ->
      info.open(modResolveInfo.resolveInfo(), pos, accessibility));

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
      info.renameOp(ctx, AnyDef.fromVar(symbol), renamedOpDecl, bind, true);
    });

    return true;
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
          // ^ yes, following command may try to open this module
        }

        yield resolveModule(context, thisReporter, mod.sourcePos(), mod.name(), newCtx -> resolveStmt(mod.contents(), newCtx));
      }
      case Command.Import cmd -> {
        var result = resolveImport(loader, context, thisReporter, cmd.sourcePos(), resolveInfo,
          cmd.path(), cmd.asName(), cmd.accessibility());

        if (result != null) {
          // TODO: i guess we won't use `ResolveInfo#commands` when it fails to resolve
          var ser = new SerImport(cmd.path(), result.importName.ids(), cmd.accessibility() == Stmt.Accessibility.Public);
          yield new ResolvingStmt.ImportCmd(ser);
        }

        yield null;
      }
      case Command.Open cmd -> {
        var success = resolveOpen(context, thisReporter, cmd.sourcePos(), resolveInfo,
          cmd.path(), cmd.accessibility(), cmd.useHide(), cmd.openExample());
        if (success) {
          var ser = new SerOpen(cmd.accessibility() == Stmt.Accessibility.Private, cmd.path(), SerUseHide.from(cmd.useHide()));
          yield new ResolvingStmt.OpenCmd(ser);
        }
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
