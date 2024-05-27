// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.ImmutableSet;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import kala.tuple.Tuple3;
import org.aya.primitive.PrimFactory;
import org.aya.primitive.ShapeFactory;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.PhysicalModuleContext;
import org.aya.resolve.error.NameProblem;
import org.aya.resolve.module.ModuleLoader;
import org.aya.syntax.compile.JitDef;
import org.aya.syntax.concrete.stmt.*;
import org.aya.syntax.core.def.TyckAnyDef;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.ref.DefVar;
import org.aya.syntax.ref.ModulePath;
import org.aya.syntax.ref.QName;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.Panic;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * The .ayac file representation.
 *
 * @param imports   The modules that this ayac imports. Absolute path.
 * @param exports   Each name consist of {@code This Module Name}, {@code Export Module Name} and {@code Symbol Name}
 * @param reExports key: a imported module that is in {@param imports}
 * @author kiva
 */
public record CompiledModule(
  @NotNull ImmutableSeq<SerImport> imports,
  @NotNull SerExport exports,
  @NotNull ImmutableMap<ModulePath, SerUseHide> reExports,
  @NotNull ImmutableMap<QName, SerBind> serOps,
  @NotNull ImmutableMap<QName, SerRenamedOp> opRename
) implements Serializable {
  public record DeState(@NotNull ClassLoader loader) {
    public @NotNull String classNameBy(@NotNull QName name) {
      var module = name.module().module().module();
      var virtualModulePath = module.drop(name.module().fileModuleSize());
      var moduleClassReference = module.view().prepended(AyaSerializer.PACKAGE_BASE).joinToString(".");
      var defClassName = virtualModulePath.view().appended(name.name()).joinToString("$");
      return STR."\{moduleClassReference}$\{defClassName}";
    }

    public @NotNull JitDef resolve(@NotNull QName name) {
      try {
        var clazz = loader.loadClass(classNameBy(name));
        var fieldInstance = clazz.getField(AyaSerializer.STATIC_FIELD_INSTANCE);
        fieldInstance.setAccessible(true);
        return (JitDef) fieldInstance.get(null);
      } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
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

  /** @see org.aya.syntax.concrete.stmt.UseHide */
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

  /**
   * @param exports (Unqualified Name -> Candidates)
   */
  record SerExport(
    @NotNull ImmutableMap<String, ImmutableSet<ImmutableSeq<String>>> exports
  ) implements Serializable {
    public boolean isExported(@NotNull ModulePath module, @NotNull QName qname) {
      var qmod = qname.asStringSeq();
      assert qmod.sizeGreaterThanOrEquals(module.module().size());
      var component = ModuleName.from(qmod.drop(module.module().size()));

      // A QName refers to a def,
      // which means it was defined in {module} if `component == This`;
      //                    defined in {component} if `component != This`
      return exports.getOption(qname.name())
        .map(components -> components.contains(component.ids()))
        .getOrDefault(false);
    }
  }

  public static @NotNull CompiledModule from(@NotNull ResolveInfo resolveInfo) {
    if (!(resolveInfo.thisModule() instanceof PhysicalModuleContext ctx)) {
      // TODO[kiva]: how to reach here?
      throw new UnsupportedOperationException();
    }

    var serialization = new Serialization(resolveInfo, MutableMap.create());

    var exports = ctx.exports().symbols().view().map((k, vs) ->
      Tuple.of(k, ImmutableSet.from(vs.keysView().map(ModuleName::ids))));

    var imports = resolveInfo.imports().view().map((k, v) ->
      new SerImport(v.resolveInfo().thisModule().modulePath(),
        k.ids(), v.reExport())).toImmutableSeq();
    return new CompiledModule(imports,
      new SerExport(ImmutableMap.from(exports)),
      ImmutableMap.from(resolveInfo.reExports().view()
        .map((k, v) -> Tuple.of(
          resolveInfo.imports()
            .get(k)   // should not fail
            .resolveInfo().thisModule().modulePath(),
          SerUseHide.from(v)))),
      // serialization.serDefs.toImmutableSeq(),
      ImmutableMap.from(serialization.serOps),
      ImmutableMap.from(resolveInfo.opRename().view().map((k, v) -> {
          var name = k.qualifiedName();
          var info = v.renamed().opInfo();
          var renamed = new SerRenamedOp(info, serialization.serBind(v.bind()));
          return Tuple.of(v.reExport(), name, renamed);
        })
        .filter(Tuple3::head) // should not serialize publicly renamed ops from upstreams
        .map(Tuple3::tail))
    );
  }

  private record Serialization(
    @NotNull ResolveInfo resolveInfo,
    @NotNull MutableMap<QName, SerBind> serOps
  ) {
    private void serOp(@NotNull TyckDef def) {
      var concrete = def.ref().concrete;
      serOps.put(new QName(def.ref()), serBind(concrete.bindBlock()));
    }

    private @NotNull SerBind serBind(@NotNull BindBlock bindBlock) {
      if (bindBlock == BindBlock.EMPTY) return SerBind.EMPTY;
      var loosers = bindBlock.resolvedLoosers().get().map(x -> TyckAnyDef.make(x.core).qualifiedName());
      var tighters = bindBlock.resolvedTighters().get().map(x -> TyckAnyDef.make(x.core).qualifiedName());
      return new SerBind(loosers, tighters);
    }
  }

  public @NotNull ResolveInfo toResolveInfo(
    @NotNull ModuleLoader loader, @NotNull PhysicalModuleContext context
  ) {
    var state = new DeState(getClass().getClassLoader());
    return toResolveInfo(loader, context, state, new PrimFactory(), new ShapeFactory());
  }
  public @NotNull ResolveInfo toResolveInfo(
    @NotNull ModuleLoader loader, @NotNull PhysicalModuleContext context, @NotNull CompiledModule.DeState state,
    @NotNull PrimFactory primFactory, @NotNull ShapeFactory shapeFactory
  ) {
    var resolveInfo = new ResolveInfo(context, primFactory, shapeFactory);
    shallowResolve(loader, resolveInfo);
    deOp(state, resolveInfo);
    return resolveInfo;
  }

  /**
   * like {@link org.aya.resolve.visitor.StmtPreResolver} but only resolve import
   */
  private void shallowResolve(@NotNull ModuleLoader loader, @NotNull ResolveInfo thisResolve) {
    for (var anImport : imports) {
      var modName = anImport.path;
      var modRename = ModuleName.qualified(anImport.rename);
      var isPublic = anImport.isPublic;
      var success = loader.load(modName);
      if (success == null)
        thisResolve.thisModule().reportAndThrow(new NameProblem.ModNotFoundError(modName, SourcePos.SER));
      thisResolve.imports().put(modRename, new ResolveInfo.ImportInfo(success, isPublic));
      var mod = success.thisModule();
      thisResolve.thisModule().importModule(modRename, mod, isPublic ? Stmt.Accessibility.Public : Stmt.Accessibility.Private, SourcePos.SER);
      reExports.getOption(modName).forEach(useHide -> thisResolve.thisModule().openModule(modRename,
        Stmt.Accessibility.Public,
        useHide.names().map(x -> new QualifiedID(SourcePos.SER, x)),
        useHide.renames().map(x -> new WithPos<>(SourcePos.SER, x)),
        SourcePos.SER, useHide.isUsing() ? UseHide.Strategy.Using : UseHide.Strategy.Hiding));
      var acc = this.reExports.containsKey(modName)
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
      opSet.bind(opDecl, OpDecl.BindPred.Looser, target, SourcePos.SER);
    });
    bind.tighters().forEach(tighter -> {
      var target = resolveOp(resolveInfo, state, tighter);
      opSet.bind(opDecl, OpDecl.BindPred.Tighter, target, SourcePos.SER);
    });
  }

  private @NotNull OpDecl resolveOp(@NotNull ResolveInfo resolveInfo, @NotNull CompiledModule.DeState state, @NotNull QName name) {
    return resolveInfo.resolveOpDecl(state.resolve(name));
  }

  private void export(@NotNull PhysicalModuleContext context, @NotNull QName qname, @NotNull DefVar<?, ?> ref) {
    var modName = context.modulePath();
    var qmodName = ModuleName.from(qname.asStringSeq().drop(modName.module().size()));
    export(context, qmodName, qname.name(), ref);
  }

  private void export(
    @NotNull PhysicalModuleContext context,
    @NotNull ModuleName component,
    @NotNull String name,
    @NotNull DefVar<?, ?> var
  ) {
    var success = context.exportSymbol(component, name, var);
    assert success : "DuplicateExportError should not happen in CompiledModule";
  }

  private boolean isExported(@NotNull ModulePath module, @NotNull QName qname) {
    return exports.isExported(module, qname);
  }
}
