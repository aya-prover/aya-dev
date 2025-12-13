// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.ImmutableSet;
import kala.collection.mutable.MutableLinkedHashMap;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import org.aya.compiler.serializers.AyaSerializer;
import org.aya.compiler.serializers.NameSerializer;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.ModuleContext;
import org.aya.resolve.context.PhysicalModuleContext;
import org.aya.resolve.module.ModuleLoader;
import org.aya.resolve.salt.AyaBinOpSet;
import org.aya.states.primitive.PrimFactory;
import org.aya.states.primitive.ShapeFactory;
import org.aya.syntax.compile.*;
import org.aya.syntax.concrete.stmt.*;
import org.aya.syntax.context.ModuleExport;
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
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.EnumMap;

/// The .ayac file representation.
///
/// @param imports         The modules that this ayac imports. Absolute path.
/// @param exports         Whether certain definition is exported. Re-exported symbols will not be here.
/// @param importReExports key: an imported module that is in {@param imports}
/// @param localReExports  key: a module defined in this module
/// @author kiva
public record CompiledModule(
  @NotNull ImmutableMap<ModuleName, SerModuleExport> moduleExport,
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

  record SerModuleExport(
    @NotNull ImmutableMap<String, QName> symbols,
    @NotNull ImmutableMap<ModuleName.Qualified, QPath> modules
  ) implements Serializable { }

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

  record SerQualifiedID(@NotNull ModuleName component, @NotNull String name) implements Serializable {
    public static @NotNull SerQualifiedID from(@NotNull QualifiedID qid) {
      return new SerQualifiedID(qid.component(), qid.name());
    }
    public @NotNull QualifiedID make() { return new QualifiedID(SourcePos.SER, component, name); }
  }

  /// @see UseHide.Rename
  record SerRename(@NotNull SerQualifiedID qid, @NotNull String to) implements Serializable {
    public static @NotNull SerRename from(@NotNull UseHide.Rename rename) {
      return new SerRename(SerQualifiedID.from(rename.name()), rename.to());
    }
    public @NotNull UseHide.Rename make() { return new UseHide.Rename(qid.make(), to); }
  }

  /// @see UseHide
  record SerUseHide(
    boolean isUsing,
    @NotNull ImmutableSeq<SerQualifiedID> names,
    @NotNull ImmutableSeq<SerRename> renames
  ) implements Serializable {
    public static @NotNull SerUseHide from(@NotNull UseHide useHide) {
      return new SerUseHide(
        useHide.strategy() == UseHide.Strategy.Using,
        useHide.list().map(x -> SerQualifiedID.from(x.id())),
        useHide.renaming().map(it -> SerRename.from(it.data()))
      );
    }
  }

  public static @NotNull CompiledModule from(@NotNull ResolveInfo resolveInfo, @NotNull ImmutableSeq<TyckDef> defs) {
    if (!(resolveInfo.thisModule() instanceof PhysicalModuleContext ctx)) {
      return Panic.unreachable();
    }

    var serialization = new Serialization(resolveInfo, MutableMap.create());
    defs.forEach(serialization::serOp);

    // TODO: i guess we can obtain module path from exports...
    var moduleExport = serialize(resolveInfo.modulePath(), resolveInfo.thisModule().exports());

    // var exports = ctx.exports().symbols().keysView();
    //
    // var imports = resolveInfo.imports().view().map((k, v) ->
    //   new SerImport(v.resolveInfo().modulePath(),
    //     k.ids(), v.reExport())).toSeq();
    // var serExport = ImmutableSet.from(exports);
    // var importReExports = MutableMap.<ModulePath, SerUseHide>create();
    // var localReExports = MutableMap.<ModuleName.Qualified, SerUseHide>create();
    // resolveInfo.reExports().forEach((qualified, useHide) -> {
    //   var imported = resolveInfo.imports().getOrNull(qualified);
    //   if (imported != null) importReExports.put(imported.resolveInfo().modulePath(), SerUseHide.from(useHide));
    //   else localReExports.put(qualified, SerUseHide.from(useHide));
    // });
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

    return new CompiledModule(
      moduleExport,
      serOps, prims, opRename);
  }

  private static @NotNull ImmutableMap<ModuleName, SerModuleExport> serialize(
    @NotNull ModulePath thisModulePath,
    @NotNull ModuleExport export
  ) {
    var definedModules = MutableMap.<ModuleName, SerModuleExport>create();

    var queue = MutableLinkedHashMap.<ModuleName, ModuleExport>of(ModuleName.This, export);

    export.modules().forEach((_, mod) -> {
      if (mod.qualifiedPath().fileModule().equals(thisModulePath)) {
        var name = mod.qualifiedPath().module().removePrefix(thisModulePath);
        assert name != null;
        queue.put(name, mod);
      }
    });

    queue.forEach((modName, mod) -> {
      var symbols = MutableMap.<String, QName>create();
      var modules = MutableMap.<ModuleName.Qualified, QPath>create();

      mod.symbols().forEach((name, def) -> {
        symbols.put(name, AnyDef.fromVar(def).qualifiedName());
      });

      mod.modules().forEach((name, myMod) -> {
        modules.put(name, myMod.qualifiedPath());
      });

      definedModules.put(modName, new SerModuleExport(
        ImmutableMap.from(symbols),
        ImmutableMap.from(modules)));
    });

    return ImmutableMap.from(definedModules);
  }

  public static class MyModuleLoader {
    public final @NotNull ModuleLoader loader;
    public final @NotNull DeState state;
    /// ModulePath to currently deserializing module
    public final @NotNull ModulePath thisModulePath;
    private final @NotNull MutableMap<ModulePath, ModuleExport> cache = MutableMap.create();
    private final @NotNull MutableMap<ModuleName.Qualified, ModuleExport> subModules = MutableMap.create();
    public final @NotNull ImmutableMap<QName, JitDef> thisDefs;

    public MyModuleLoader(
      @NotNull ModuleLoader loader,
      @NotNull DeState state,
      @NotNull ModulePath thisModulePath,
      @NotNull ImmutableSeq<JitDef> thisDefs
    ) {
      this.loader = loader;
      this.state = state;
      this.thisModulePath = thisModulePath;
      this.thisDefs = thisDefs.associateBy(JitUnit::qualifiedName);
    }

    private @NotNull ModuleExport loadFileLevel(@NotNull QPath path) {
      var key = path.fileModule();
      var exists = cache.getOrNull(key);
      if (exists != null) return exists;

      // TODO: handle error
      var loaded = loader.load(path.fileModule()).get().thisModule().exports();
      cache.put(key, loaded);
      return loaded;
    }

    public @NotNull ModuleExport load(@NotNull QPath path) {
      if (path.fileModule().equals(thisModulePath)) {
        switch (path.localModule()) {
          case ModuleName.Qualified qualified -> subModules.get(qualified);   // never fail, i guess
          case ModuleName.ThisRef _ -> {
            // should be unreachable, there is no way to reference the file module itself in itself.
            return Panic.unreachable();
          }
        }
      }

      var fileLevel = loadFileLevel(path);
      var name = path.localModule();
      return switch (name) {
        case ModuleName.ThisRef _ -> fileLevel;
        case ModuleName.Qualified qualified ->
          // never fail, unless the core is corrupted. maybe we can provide some useful information in that case?
          fileLevel.modules().get(qualified);
      };
    }

    public @NotNull AnyDefVar load(@NotNull QName name) {
      if (name.module().fileModule().equals(thisModulePath)) {
        return new CompiledVar(this.thisDefs.get(name));      // should not fail
      }

      // TODO: will we support hot compilation? or we can just assume this is a CompiledVar
      return load(name.module()).symbols().get(name.name());
    }

    public void acceptSubmodule(@NotNull ModuleName.Qualified name, @NotNull ModuleExport export) {
      assert export.qualifiedPath()
        .equals(QPath.fileLevel(thisModulePath).derive(name));

      var exists = this.subModules.put(name, export);
      assert exists.isEmpty();
    }
  }

  /// Deserialize [#thisModulePath] from [#modules], if `thisModulePath` is a sub module,
  /// then deserialized module must be put to [#loader].
  private @NotNull ModuleExport deserialize(
    @NotNull QPath thisModulePath,
    @NotNull MyModuleLoader loader,
    @NotNull ImmutableMap<ModuleName, SerModuleExport> modules
  ) {
    @Nullable ModuleName.Qualified subModuleName = null;

    switch (thisModulePath.localModule()) {
      case ModuleName.Qualified qualified -> subModuleName = qualified;
      case ModuleName.ThisRef _ -> { }
    }

    if (subModuleName != null) {
      var cache = loader.subModules.getOrNull(subModuleName);
      if (cache != null) return cache;
    }

    var export = new ModuleExport(thisModulePath);
    var thisSer = modules.get(thisModulePath.localModule());
    thisSer.symbols().forEach((name, def) -> {
      var symbol = loader.load(def);
      export.symbols().put(name, symbol);
    });

    thisSer.modules().forEach((name, modPath) -> {
      if (modPath.fileModule().equals(loader.thisModulePath)) {
        // the call graph is equal to the dependency graph, which is a DAG
        var deser = deserialize(modPath, loader, modules);
        export.modules().put(name, deser);
      } else {
        // otherwise, we just load module
        var mod = loader.load(modPath);
        export.modules().put(name, mod);
      }
    });

    if (subModuleName != null) {
      loader.acceptSubmodule(subModuleName, export);
    }

    return export;
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
    var allDefs = MutableList.<JitDef>create();
    var rootClass = state.topLevelClass(context.modulePath());
    for (var jitClass : rootClass.getDeclaredClasses()) {
      var object = DeState.getJitDef(jitClass);
      if (!(object instanceof JitDef jitDef)) continue;
      allDefs.append(jitDef);
      loadModule(primFactory, shapeFactory, context, jitDef, reporter);
    }

    var root = deserialize(context.qualifiedPath(),
      new MyModuleLoader(loader, state, context.modulePath(), allDefs.toSeq()),
      this.moduleExport);

    // kinda slow, but who cares??
    context.exports.symbols().putAll(root.symbols());
    context.exports.modules().putAll(root.modules());

    primDefs.forEach((_, qname) ->
      primFactory.definePrim((JitPrim) state.resolve(qname)));
    deOp(state, resolveInfo);
    return resolveInfo;
  }

  private void loadModule(
    @NotNull PrimFactory primFactory, @NotNull ShapeFactory shapeFactory,
    @NotNull PhysicalModuleContext context, @NotNull JitDef jitDef, @NotNull Reporter reporter
  ) {
    var metadata = jitDef.metadata();
    // export(context, jitDef);
    switch (jitDef) {
      case JitData data -> {
        // The accessibility doesn't matter, this context is readonly
        // var innerCtx = context.derive(data.name());
        // for (var constructor : data.constructors()) {
        //   var success = innerCtx.defineSymbol(new CompiledVar(constructor), Stmt.Accessibility.Public, SourcePos.SER, reporter);
        //   if (!success) Panic.unreachable();
        // }
        // var success = context.importModuleContext(
        //   ModuleName.This.resolve(data.name()),
        //   innerCtx, Stmt.Accessibility.Public, SourcePos.SER, reporter);
        // if (!success) Panic.unreachable();
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

  /// like [org.aya.resolve.visitor.StmtPreResolver] but only resolve import
  // private void shallowResolve(@NotNull ModuleLoader loader, @NotNull ResolveInfo thisResolve, @NotNull Reporter reporter) {
  //   for (var anImport : imports) {
  //     var modName = anImport.path;
  //     var modRename = ModuleName.qualified(anImport.rename);
  //     var isPublic = anImport.isPublic;
  //
  //     var loaded = loader.load(modName)
  //       .getOrThrow(() -> new Panic("Unable to load a dependency module of a compiled module"));
  //
  //     thisResolve.imports().put(modRename, new ResolveInfo.ImportInfo(loaded, isPublic));
  //     var mod = loaded.thisModule();
  //     var success = thisResolve.thisModule()
  //       .importModuleContext(modRename, mod, isPublic ? Stmt.Accessibility.Public : Stmt.Accessibility.Private, SourcePos.SER, reporter);
  //     if (!success) Panic.unreachable();
  //     var useHide = importReExports.getOrNull(modName);
  //     if (useHide != null) {
  //       success = thisResolve.thisModule().openModule(modRename,
  //         Stmt.Accessibility.Public,
  //         useHide.names().map(SerQualifiedID::make),
  //         useHide.renames().map(x -> new WithPos<>(SourcePos.SER, x.make())),
  //         SourcePos.SER, useHide.isUsing() ? UseHide.Strategy.Using : UseHide.Strategy.Hiding,
  //         reporter);
  //
  //       if (!success) Panic.unreachable();
  //     }
  //     var acc = importReExports.containsKey(modName)
  //       ? Stmt.Accessibility.Public
  //       : Stmt.Accessibility.Private;
  //     thisResolve.open(loaded, SourcePos.SER, acc);
  //   }
  // }

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

  /// @see org.aya.syntax.context.ModuleExport#map
  /// @see org.aya.syntax.context.ModuleExport#filter
  // private void export(@NotNull PhysicalModuleContext context, @NotNull JitDef def) {
  //   boolean success = true;
  //   var module = def.qualifiedName().module().localModule();
  //   if (module instanceof ModuleName.ThisRef && exports.contains(def.name())) {
  //     success = context.exportSymbol(def.name(), new CompiledVar(def));
  //   }
  //   for (int i = 0; i < module.length(); ++i) {
  //     var qualified = new ModuleName.Qualified(module.ids().drop(i));
  //     var local = localReExports.getOrNull(qualified);
  //     if (local == null) continue;
  //     var contains = local.names.find(qid ->
  //         qid.name.contentEquals(def.name()) &&
  //           qualified.concat(qid.component).equals(module))
  //       .getOrNull();
  //     if (local.isUsing && contains != null) {
  //       var rename = local.renames.find(it -> it.qid.equals(contains)).getOrNull();
  //       if (rename != null) {
  //         success = context.exportSymbol(rename.to, new CompiledVar(def)) && success;
  //       } else {
  //         success = context.exportSymbol(def.name(), new CompiledVar(def)) && success;
  //       }
  //     } else if (!local.isUsing && contains == null) {
  //       success = context.exportSymbol(def.name(), new CompiledVar(def)) && success;
  //     }
  //   }
  //   assert success : "DuplicateExportError should not happen in CompiledModule";
  // }
}
