// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.ImmutableSet;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import org.aya.compiler.serializers.AyaSerializer;
import org.aya.compiler.serializers.NameSerializer;
import org.aya.primitive.PrimFactory;
import org.aya.primitive.ShapeFactory;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.Context;
import org.aya.resolve.context.PhysicalModuleContext;
import org.aya.resolve.error.NameProblem;
import org.aya.resolve.module.ModuleLoader;
import org.aya.syntax.compile.JitData;
import org.aya.syntax.compile.JitDef;
import org.aya.syntax.compile.JitFn;
import org.aya.syntax.compile.JitPrim;
import org.aya.syntax.concrete.stmt.*;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.core.repr.AyaShape;
import org.aya.syntax.core.repr.ShapeRecognition;
import org.aya.syntax.ref.*;
import org.aya.util.ArrayUtil;
import org.aya.util.Panic;
import org.aya.util.binop.OpDecl;
import org.aya.util.position.SourcePos;
import org.aya.util.position.WithPos;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * The .ayac file representation.
 *
 * @param imports   The modules that this ayac imports. Absolute path.
 * @param exports   Whether certain definition is exported. Re-exported symbols will not be here.
 * @param reExports key: an imported module that is in {@param imports}
 * @author kiva
 */
public record CompiledModule(
  @NotNull ImmutableSeq<SerImport> imports,
  @NotNull ImmutableSet<String> exports,
  @NotNull ImmutableMap<ModulePath, SerUseHide> reExports,
  @NotNull ImmutableMap<QName, SerBind> serOps,
  @NotNull ImmutableMap<QName, SerRenamedOp> opRename
) implements Serializable {
  public record DeState(@NotNull ClassLoader loader) {
    public @NotNull Class<?> topLevelClass(@NotNull ModulePath name) {
      try {
        return loader.loadClass(NameSerializer.getModuleClassName(QPath.fileLevel(name)));
      } catch (ClassNotFoundException e) {
        throw new Panic(e);
      }
    }

    public @NotNull JitDef resolve(@NotNull QName name) {
      try {
        return (JitDef) getJitDef(loader.loadClass(NameSerializer.getClassName(name)));
      } catch (ClassNotFoundException e) {
        throw new Panic(e);
      }
    }
    private static Object getJitDef(Class<?> clazz) {
      try {
        var fieldInstance = clazz.getField(AyaSerializer.STATIC_FIELD_INSTANCE);
        fieldInstance.setAccessible(true);
        return fieldInstance.get(null);
      } catch (NoSuchFieldException | IllegalAccessException e) {
        throw new Panic(e);
      }
    }
  }

  record SerBind(@NotNull ImmutableSeq<QName> loosers, @NotNull ImmutableSeq<QName> tighters) implements Serializable {
    public static final SerBind EMPTY = new SerBind(ImmutableSeq.empty(), ImmutableSeq.empty());
  }

  record SerRenamedOp(@NotNull OpDecl.OpInfo info, @NotNull SerBind bind) implements Serializable { }

  /**
   * @param rename not empty
   */
  record SerImport(
    @NotNull ModulePath path, @NotNull ImmutableSeq<String> rename,
    boolean isPublic) implements Serializable { }

  /** @see UseHide */
  record SerUseHide(
    boolean isUsing,
    @NotNull ImmutableSeq<ImmutableSeq<String>> names,
    @NotNull ImmutableSeq<UseHide.Rename> renames
  ) implements Serializable {
    public static @NotNull SerUseHide from(@NotNull UseHide useHide) {
      return new SerUseHide(
        useHide.strategy() == UseHide.Strategy.Using,
        useHide.list().map(x -> x.id().ids()),
        useHide.renaming().map(WithPos::data)
      );
    }
  }

  public static @NotNull CompiledModule from(@NotNull ResolveInfo resolveInfo, @NotNull ImmutableSeq<TyckDef> defs) {
    if (!(resolveInfo.thisModule() instanceof PhysicalModuleContext ctx)) {
      return Panic.unreachable();
    }

    var serialization = new Serialization(resolveInfo, MutableMap.create());
    defs.forEach(serialization::serOp);

    var exports = ctx.exports().symbols().keysView();

    var imports = resolveInfo.imports().view().map((k, v) ->
      new SerImport(v.resolveInfo().modulePath(),
        k.ids(), v.reExport())).toSeq();
    var serExport = ImmutableSet.from(exports);
    var reExports = ImmutableMap.from(resolveInfo.reExports().view()
      .map((k, v) -> Tuple.of(
        resolveInfo.imports()
          .get(k)   // should not fail
          .resolveInfo().modulePath(),
        SerUseHide.from(v))));
    var serOps = ImmutableMap.from(serialization.serOps);
    record RenameData(boolean reExport, QName name, SerRenamedOp renamed) { }
    var opRename = ImmutableMap.from(resolveInfo.opRename().view().map((k, v) -> {
        var name = k.qualifiedName();
        var info = v.renamed().opInfo();
        var renamed = new SerRenamedOp(info, serialization.serBind(v.bind()));
        return new RenameData(v.reExport(), name, renamed);
      })
      .filter(RenameData::reExport) // should not serialize publicly renamed ops from upstreams
      .map(data -> Tuple.of(data.name, data.renamed)));

    return new CompiledModule(imports, serExport, reExports, serOps, opRename);
  }

  private record Serialization(
    @NotNull ResolveInfo resolveInfo,
    @NotNull MutableMap<QName, SerBind> serOps
  ) {
    private void serOp(@NotNull TyckDef def) {
      var concrete = def.ref().concrete;
      if (concrete.opInfo() != null)
        serOps.put(new QName(def.ref()), serBind(concrete.bindBlock()));
    }

    private @NotNull SerBind serBind(@NotNull BindBlock bindBlock) {
      if (bindBlock == BindBlock.EMPTY) return SerBind.EMPTY;
      var loosers = bindBlock.resolvedLoosers().get().map(x -> AnyDef.fromVar(x).qualifiedName());
      var tighters = bindBlock.resolvedTighters().get().map(x -> AnyDef.fromVar(x).qualifiedName());
      return new SerBind(loosers, tighters);
    }
  }

  public @NotNull ResolveInfo toResolveInfo(
    @NotNull ModuleLoader loader, @NotNull PhysicalModuleContext context,
    @NotNull ClassLoader classLoader, @NotNull PrimFactory primFactory
  ) throws Context.ResolvingInterruptedException {
    var state = new DeState(classLoader);
    return toResolveInfo(loader, context, state, primFactory, new ShapeFactory());
  }
  public @NotNull ResolveInfo toResolveInfo(
    @NotNull ModuleLoader loader, @NotNull PhysicalModuleContext context, @NotNull CompiledModule.DeState state,
    @NotNull PrimFactory primFactory, @NotNull ShapeFactory shapeFactory
  ) throws Context.ResolvingInterruptedException {
    var resolveInfo = new ResolveInfo(context, primFactory, shapeFactory);
    shallowResolve(loader, resolveInfo);
    loadModule(primFactory, shapeFactory, context, state.topLevelClass(context.modulePath()));
    deOp(state, resolveInfo);
    return resolveInfo;
  }

  private void loadModule(
    @NotNull PrimFactory primFactory, @NotNull ShapeFactory shapeFactory,
    @NotNull PhysicalModuleContext context, @NotNull Class<?> rootClass
  ) throws Context.ResolvingInterruptedException {
    for (var jitClass : rootClass.getDeclaredClasses()) {
      var object = DeState.getJitDef(jitClass);
      // Not all JitUnit are JitDef, see JitMatchy
      if (!(object instanceof JitDef jitDef)) continue;
      var metadata = jitDef.metadata();
      if (jitDef instanceof JitPrim || isExported(jitDef.name()))
        export(context, jitDef.name(), new CompiledVar(jitDef));
      switch (jitDef) {
        case JitData data -> {
          // The accessibility doesn't matter, this context is readonly
          var innerCtx = context.derive(data.name());
          for (var constructor : data.constructors()) {
            innerCtx.defineSymbol(new CompiledVar(constructor), Stmt.Accessibility.Public, SourcePos.SER);
          }
          context.importModuleContext(
            ModuleName.This.resolve(data.name()),
            innerCtx, Stmt.Accessibility.Public, SourcePos.SER);
          if (metadata.shape() != -1) {
            var recognition = new ShapeRecognition(AyaShape.values()[metadata.shape()],
              ImmutableMap.from(ArrayUtil.zip(metadata.recognition(),
                data.constructors())));
            shapeFactory.bonjour(jitDef, recognition);
          }
        }
        case JitFn fn -> {
          if (metadata.shape() != -1) {
            var recognition = new ShapeRecognition(AyaShape.values()[metadata.shape()],
              ImmutableMap.empty());
            shapeFactory.bonjour(fn, recognition);
          }
        }
        case JitPrim prim -> primFactory.definePrim(prim);
        default -> { }
      }
    }
  }

  /**
   * like {@link org.aya.resolve.visitor.StmtPreResolver} but only resolve import
   */
  private void shallowResolve(@NotNull ModuleLoader loader, @NotNull ResolveInfo thisResolve) throws Context.ResolvingInterruptedException {
    for (var anImport : imports) {
      var modName = anImport.path;
      var modRename = ModuleName.qualified(anImport.rename);
      var isPublic = anImport.isPublic;
      var success = loader.load(modName);
      if (success == null)
        thisResolve.thisModule().reportAndThrow(new NameProblem.ModNotFoundError(modName, SourcePos.SER));
      thisResolve.imports().put(modRename, new ResolveInfo.ImportInfo(success, isPublic));
      var mod = success.thisModule();
      thisResolve.thisModule().importModuleContext(modRename, mod, isPublic ? Stmt.Accessibility.Public : Stmt.Accessibility.Private, SourcePos.SER);
      reExports.getOption(modName).forEachChecked(useHide -> thisResolve.thisModule().openModule(modRename,
        Stmt.Accessibility.Public,
        useHide.names().map(x -> new QualifiedID(SourcePos.SER, x)),
        useHide.renames().map(x -> new WithPos<>(SourcePos.SER, x)),
        SourcePos.SER, useHide.isUsing() ? UseHide.Strategy.Using : UseHide.Strategy.Hiding));
      var acc = reExports.containsKey(modName)
        ? Stmt.Accessibility.Public
        : Stmt.Accessibility.Private;
      thisResolve.open(success, SourcePos.SER, acc);
    }
  }

  /**
   * like {@link org.aya.resolve.visitor.StmtResolver} but only resolve operator
   */
  private void deOp(@NotNull CompiledModule.DeState state, @NotNull ResolveInfo resolveInfo) {
    // deserialize renamed operator
    opRename.view().forEach((name, serOp) -> {
      var defVar = state.resolve(name);
      var opDecl = new ResolveInfo.RenamedOpDecl(serOp.info);
      resolveInfo.renameOp(resolveInfo.thisModule(), defVar, opDecl, BindBlock.EMPTY, true);
      // ^ always use empty bind block bc we will resolve the bind here!
      deBindDontCare(resolveInfo, opDecl, state, serOp.bind);
    });
    // and their bindings
    serOps.view().forEach((serOp, bind) -> deBindDontCare(resolveInfo, state.resolve(serOp), state, bind));
  }

  private void deBindDontCare(
    @NotNull ResolveInfo resolveInfo,
    @NotNull OpDecl opDecl,
    @NotNull CompiledModule.DeState state,
    @NotNull SerBind bind
  ) {
    var opSet = resolveInfo.opSet();
    opSet.ensureHasElem(opDecl);
    bind.loosers().forEach(looser -> {
      var target = resolveOp(resolveInfo, state, looser);
      // TODO: check result
      opSet.bind(opDecl, OpDecl.BindPred.Looser, target, SourcePos.SER);
    });
    bind.tighters().forEach(tighter -> {
      var target = resolveOp(resolveInfo, state, tighter);
      // TODO: check result
      opSet.bind(opDecl, OpDecl.BindPred.Tighter, target, SourcePos.SER);
    });
  }

  private @NotNull OpDecl resolveOp(@NotNull ResolveInfo resolveInfo, @NotNull CompiledModule.DeState state, @NotNull QName name) {
    return resolveInfo.resolveOpDecl(state.resolve(name));
  }

  private void export(
    @NotNull PhysicalModuleContext context,
    @NotNull String name,
    @NotNull AnyDefVar var
  ) {
    var success = context.exportSymbol(name, var);
    assert success : "DuplicateExportError should not happen in CompiledModule";
  }

  private boolean isExported(@NotNull String name) { return exports.contains(name); }
}
