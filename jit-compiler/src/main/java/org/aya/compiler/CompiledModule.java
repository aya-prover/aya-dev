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
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.PhysicalModuleContext;
import org.aya.resolve.module.ModuleLoader;
import org.aya.resolve.salt.AyaBinOpSet;
import org.aya.states.primitive.PrimFactory;
import org.aya.states.primitive.ShapeFactory;
import org.aya.syntax.compile.JitData;
import org.aya.syntax.compile.JitDef;
import org.aya.syntax.compile.JitFn;
import org.aya.syntax.compile.JitPrim;
import org.aya.syntax.concrete.stmt.*;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.PrimDef;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.core.repr.AyaShape;
import org.aya.syntax.core.repr.ShapeRecognition;
import org.aya.syntax.ref.*;
import org.aya.util.ArrayUtil;
import org.aya.util.Panic;
import org.aya.util.binop.OpDecl;
import org.aya.util.position.SourcePos;
import org.aya.util.position.WithPos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.EnumMap;

/**
 * The .ayac file representation.
 *
 * @param imports   The modules that this ayac imports. Absolute path.
 * @param exports   Whether certain definition is exported. Re-exported symbols will not be here.
 * @param importReExports key: an imported module that is in {@param imports}
 * @author kiva
 */
public record CompiledModule(
  @NotNull ImmutableSeq<SerImport> imports,
  @NotNull ImmutableSet<String> exports,
  @NotNull ImmutableMap<ModulePath, SerUseHide> importReExports,
  @NotNull ImmutableMap<ModulePath, SerUseHide> localReExports,
  @NotNull ImmutableMap<QName, SerBind> serOps,
  @NotNull EnumMap<PrimDef.ID, QName> primDefs,
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
    var importReExports = ImmutableMap.from(resolveInfo.reExports().view()
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
    var prims = resolveInfo.primFactory().qnameMap();

    return new CompiledModule(imports, serExport, importReExports, ImmutableMap.empty(), serOps, prims, opRename);
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
    @NotNull ClassLoader classLoader, @NotNull PrimFactory primFactory, @NotNull Reporter reporter
  ) {
    var state = new DeState(classLoader);
    return toResolveInfo(loader, context, state, primFactory, new ShapeFactory(), reporter);
  }

  public @NotNull ResolveInfo toResolveInfo(
    @NotNull ModuleLoader loader, @NotNull PhysicalModuleContext context, @NotNull CompiledModule.DeState state,
    @NotNull PrimFactory primFactory, @NotNull ShapeFactory shapeFactory, @NotNull Reporter reporter
  ) {
    var resolveInfo = new ResolveInfo(context, primFactory, shapeFactory, new AyaBinOpSet(reporter));
    shallowResolve(loader, resolveInfo, reporter);
    var rootClass = state.topLevelClass(context.modulePath());
    loadModule(primFactory, shapeFactory, context, rootClass, reporter);
    primDefs.forEach((_, qname) ->
      primFactory.definePrim((JitPrim) state.resolve(qname)));
    deOp(state, resolveInfo);
    return resolveInfo;
  }

  private void loadModule(
    @NotNull PrimFactory primFactory, @NotNull ShapeFactory shapeFactory,
    @NotNull PhysicalModuleContext context, @NotNull Class<?> rootClass, @NotNull Reporter reporter
  ) {
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
            var success = innerCtx.defineSymbol(new CompiledVar(constructor), Stmt.Accessibility.Public, SourcePos.SER, reporter);
            if (!success) Panic.unreachable();
          }
          var success = context.importModuleContext(
            ModuleName.This.resolve(data.name()),
            innerCtx, Stmt.Accessibility.Public, SourcePos.SER, reporter);
          if (!success) Panic.unreachable();
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

  /// like [org.aya.resolve.visitor.StmtPreResolver] but only resolve import
  private void shallowResolve(@NotNull ModuleLoader loader, @NotNull ResolveInfo thisResolve, @NotNull Reporter reporter) {
    for (var anImport : imports) {
      var modName = anImport.path;
      var modRename = ModuleName.qualified(anImport.rename);
      var isPublic = anImport.isPublic;

      var loaded = loader.load(modName)
        .getOrThrow(() -> new Panic("Unable to load a dependency module of a compiled module"));

      thisResolve.imports().put(modRename, new ResolveInfo.ImportInfo(loaded, isPublic));
      var mod = loaded.thisModule();
      var success = thisResolve.thisModule()
        .importModuleContext(modRename, mod, isPublic ? Stmt.Accessibility.Public : Stmt.Accessibility.Private, SourcePos.SER, reporter);
      if (!success) Panic.unreachable();
      var useHide = importReExports.getOrNull(modName);
      if (useHide != null) {
        success = thisResolve.thisModule().openModule(modRename,
          Stmt.Accessibility.Public,
          useHide.names().map(x -> new QualifiedID(SourcePos.SER, x)),
          useHide.renames().map(x -> new WithPos<>(SourcePos.SER, x)),
          SourcePos.SER, useHide.isUsing() ? UseHide.Strategy.Using : UseHide.Strategy.Hiding,
          reporter);

        if (!success) Panic.unreachable();
      }
      var acc = importReExports.containsKey(modName)
        ? Stmt.Accessibility.Public
        : Stmt.Accessibility.Private;
      thisResolve.open(loaded, SourcePos.SER, acc);
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
      var success = opSet.bind(opDecl, OpDecl.BindPred.Looser, target, SourcePos.SER);
      if (!success) Panic.unreachable();
    });
    bind.tighters().forEach(tighter -> {
      var target = resolveOp(resolveInfo, state, tighter);
      var success = opSet.bind(opDecl, OpDecl.BindPred.Tighter, target, SourcePos.SER);
      if (!success) Panic.unreachable();
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
